package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.RequiredPublisher;
import esa.mo.tools.stubgen.StubUtils;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.InteractionPatternEnum;
import esa.mo.tools.stubgen.specification.OperationSummary;
import esa.mo.tools.stubgen.specification.ServiceSummary;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.InterfaceWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.tools.stubgen.specification.FieldInfo;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CppProvider {

    private final CppGeneratorLangs generator;

    public CppProvider(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createServiceProviderClasses(File serviceFolder, String area, String service, ServiceSummary summary, Map<String, RequiredPublisher> requiredPublishers) throws IOException {
        File providerFolder = StubUtils.createFolder(serviceFolder, CppGeneratorLangs.PROVIDER_FOLDER);

        createHandlerInterface(providerFolder, area, service, summary);
        createSkeleton(providerFolder, area, service, summary);
        createInheritanceSkeleton(providerFolder, area, service, summary);
    }

    private void createHandlerInterface(File providerFolder, String area, String service, ServiceSummary summary) throws IOException {
        String handlerName = service + "Handler";
        InterfaceWriter file = generator.createInterfaceFile(providerFolder, handlerName);
        file.addPackageStatement(area, service, CppGeneratorLangs.PROVIDER_FOLDER);
        file.addInterfaceOpenStatement(handlerName, null, "Handler interface for " + service);

        for (OperationSummary op : summary.getOperations()) {
            if (op.getPattern() == InteractionPatternEnum.PUBSUB_OP) continue; // PubSub không qua Handler

            List<CompositeField> opArgs = generator.createOperationArguments(generator.getConfig(), file, op.getArgTypes());
            CompositeField interaction = generator.createCompositeElementsDetails(file, false, "interaction",
                    TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::MALInteraction>", false), false, true, null);

            opArgs.add(interaction);

            CompositeField retType = (op.getPattern() == InteractionPatternEnum.REQUEST_OP)
                    ? generator.createOperationReturnType(file, area, service, op)
                    : null;

            // Interface phương thức ảo (pure virtual)
            file.addInterfaceMethodDeclaration("public", retType, op.getName(), opArgs, null, "Handles " + op.getName(), null, null);
        }

        file.addInterfaceCloseStatement();
        file.flush();
    }

    private void createSkeleton(File providerFolder, String area, String service, ServiceSummary summary) throws IOException {
        String skeletonName = service + "Skeleton";
        InterfaceWriter file = generator.createInterfaceFile(providerFolder, skeletonName);
        file.addPackageStatement(area, service, CppGeneratorLangs.PROVIDER_FOLDER);
        file.addInterfaceOpenStatement(skeletonName, null, "Skeleton interface for " + service);

        // TBD: Thêm createPublisher() cho PUBSUB
        file.addInterfaceCloseStatement();
        file.flush();
    }

    private void createInheritanceSkeleton(File providerFolder, String area, String service, ServiceSummary summary) throws IOException {
        String className = service + "InheritanceSkeleton";
        ClassWriter file = generator.createClassFile(providerFolder, className);
        file.addPackageStatement(area, service, CppGeneratorLangs.PROVIDER_FOLDER);

        String implementsList = service + "Handler, mo::mal::provider::MALInteractionHandler";
        file.addClassOpenStatement(className, false, true, null, implementsList, "Inheritance Skeleton for " + service);

        // Biến MALProviderSet (Mục 4.4.7)
        CompositeField providerSet = generator.createCompositeElementsDetails(file, false, "providerSet",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::provider::MALProviderSet>", false), false, true, null);
        file.addClassVariable(false, false, "protected", providerSet, false, null);

        file.addConstructorDefault(className);

        // Sinh các hàm định tuyến (Routing)
        generateRouterMethod(file, "handleSend", InteractionPatternEnum.SEND_OP, summary, service);
        generateRouterMethod(file, "handleRequest", InteractionPatternEnum.REQUEST_OP, summary, service);
        // TBD: handleSubmit, handleInvoke, handleProgress

        file.addClassCloseStatement();
        file.flush();
    }

    private void generateRouterMethod(ClassWriter file, String methodName, InteractionPatternEnum pattern, ServiceSummary summary, String serviceName) throws IOException {
        CompositeField interaction = generator.createCompositeElementsDetails(file, false, "interaction",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::MALInteraction>", false), false, true, null);
        CompositeField body = generator.createCompositeElementsDetails(file, false, "body",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::MALMessageBody>", false), false, true, null);

        MethodWriter method = file.addMethodOpenStatementOverride(null, methodName, Arrays.asList(interaction, body), null);

        method.addLine("uint16_t opNumber = interaction->getMessageHeader()->getOperation()->getValue();");
        method.addLine("switch (opNumber) {");

        for (OperationSummary op : summary.getOperations()) {
            if (op.getPattern() == pattern) {
                String opCaps = op.getName().toUpperCase();
                method.addLine("    case " + serviceName + "Helper::" + opCaps + "_OP_NUMBER: {");

                // Trích xuất parameters từ MessageBody
                String callArgs = "";
                List<FieldInfo> args = op.getArgTypes();
                if (args != null && !args.isEmpty()) {
                    for (int i = 0; i < args.size(); i++) {
                        String type = generator.createElementType(args.get(i).getSourceType(), true);
                        method.addLine("        auto arg" + i + " = std::dynamic_pointer_cast<" + type + ">(body->getBodyElement(" + i + ", nullptr));");
                        callArgs += "arg" + i + ", ";
                    }
                }

                if (pattern == InteractionPatternEnum.REQUEST_OP) {
                    method.addLine("        auto response = this->" + op.getName() + "(" + callArgs + "interaction);");
                    method.addLine("        // TBD: Encode response and send");
                } else {
                    method.addLine("        this->" + op.getName() + "(" + callArgs + "interaction);");
                }

                method.addLine("        break;");
                method.addLine("    }");
            }
        }

        method.addLine("    default:");
        method.addLine("        throw std::runtime_error(\"Unsupported operation number\");");
        method.addLine("}");

        method.addMethodCloseStatement();
    }
}