package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.StubUtils;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.InteractionPatternEnum;
import esa.mo.tools.stubgen.specification.OperationSummary;
import esa.mo.tools.stubgen.specification.ServiceSummary;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CppConsumer {

    private final CppGeneratorLangs generator;

    public CppConsumer(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createServiceConsumerClasses(File serviceFolder, String areaName, String serviceName, ServiceSummary summary) throws IOException {
        File consumerFolder = StubUtils.createFolder(serviceFolder, CppGeneratorLangs.CONSUMER_FOLDER);

        createServiceConsumerAdapter(consumerFolder, areaName, serviceName, summary);
        createServiceConsumerStub(consumerFolder, areaName, serviceName, summary);
    }

    private void createServiceConsumerAdapter(File consumerFolder, String areaName, String serviceName, ServiceSummary summary) throws IOException {
        String className = serviceName + "Adapter";
        ClassWriter file = generator.createClassFile(consumerFolder, className);
        file.addPackageStatement(areaName, serviceName, CppGeneratorLangs.CONSUMER_FOLDER);

        file.addClassOpenStatement(className, false, true, "mo::mal::consumer::MALInteractionAdapter", null, "Consumer adapter for " + serviceName + ".");

        // Các biến cơ bản trong C++ MAL
        CompositeField header = createField(file, "msgHeader", "mo::mal::transport::MALMessageHeader");
        CompositeField qos = createField(file, "qosProperties", "mo::mal::transport::MALQoSProperties");
        CompositeField error = createField(file, "error", "mo::mal::MALStandardError");

        for (OperationSummary op : summary.getOperations()) {
            String opName = op.getName();
            switch (op.getPattern()) {
                case SUBMIT_OP:
                    addVirtualCallback(file, opName + "AckReceived", Arrays.asList(header, qos));
                    addVirtualCallback(file, opName + "ErrorReceived", Arrays.asList(header, error, qos));
                    break;
                case REQUEST_OP:
                    // Dựa vào Magenta Book 4.3.3.22 (REQUEST)
                    List<CompositeField> resArgs = StubUtils.concatenateArguments(header, generator.createOperationArguments(generator.getConfig(), file, op.getRetTypes()));
                    resArgs.add(qos);
                    addVirtualCallback(file, opName + "ResponseReceived", resArgs);
                    addVirtualCallback(file, opName + "ErrorReceived", Arrays.asList(header, error, qos));
                    break;
                case INVOKE_OP:
                    addVirtualCallback(file, opName + "AckReceived", Arrays.asList(header, qos)); // Rút gọn cho ví dụ
                    addVirtualCallback(file, opName + "AckErrorReceived", Arrays.asList(header, error, qos));
                    break;
                case PROGRESS_OP:
                    addVirtualCallback(file, opName + "AckReceived", Arrays.asList(header, qos));
                    addVirtualCallback(file, opName + "UpdateErrorReceived", Arrays.asList(header, error, qos));
                    break;
                case PUBSUB_OP:
                    addVirtualCallback(file, opName + "RegisterAckReceived", Arrays.asList(header, qos));
                    addVirtualCallback(file, opName + "NotifyErrorReceived", Arrays.asList(header, error, qos));
                    break;
            }
        }

        file.addClassCloseStatement();
        file.flush();
    }

    private void createServiceConsumerStub(File consumerFolder, String area, String service, ServiceSummary summary) throws IOException {
        String className = service + "Stub";
        ClassWriter file = generator.createClassFile(consumerFolder, className);
        file.addPackageStatement(area, service, CppGeneratorLangs.CONSUMER_FOLDER);

        file.addClassOpenStatement(className, false, false, null, null, "Consumer stub for " + service + ".");

        CompositeField consumerField = createField(file, "consumer", "mo::mal::consumer::MALConsumer");
        file.addClassVariable(false, false, "private", consumerField, false, null);

        MethodWriter constructor = file.addConstructor("public", className, consumerField, false, null, "Constructor", null);
        constructor.addLine("this->consumer = consumer;");
        constructor.addMethodCloseStatement();

        // Get Consumer
        MethodWriter getter = file.addMethodOpenStatement(false, true, false, "public", false, true, consumerField, "getConsumer", null, null);
        getter.addLine("return consumer;");
        getter.addMethodCloseStatement();

        // Sinh các hàm gọi API (Mục 4.3.2)
        CompositeField msgType = createField(file, "return", "mo::mal::transport::MALMessage");

        for (OperationSummary op : summary.getOperations()) {
            String opName = op.getName();
            List<CompositeField> opArgs = generator.createOperationArguments(generator.getConfig(), file, op.getArgTypes());
            String opTypeVar = service + "Helper::" + opName.toUpperCase() + "_OP";

            switch (op.getPattern()) {
                case SEND_OP:
                    MethodWriter sendMethod = file.addMethodOpenStatement(false, false, false, "public", false, true, msgType, opName, opArgs, null);
                    sendMethod.addLine("return consumer->send(" + opTypeVar + ", {" + generator.createArgNameOrNull(op.getArgTypes()) + "});");
                    sendMethod.addMethodCloseStatement();
                    break;
                case REQUEST_OP:
                    CompositeField retType = generator.createOperationReturnType(file, area, service, op);
                    MethodWriter reqMethod = file.addMethodOpenStatement(false, false, false, "public", false, true, retType, opName, opArgs, null);
                    
                    reqMethod.addLine("auto responseBody = consumer->request(" + opTypeVar + ", {" + generator.createArgNameOrNull(op.getArgTypes()) + "});");
                    // Ở đây cần logic trích xuất từ responseBody (Tùy thuộc MAL C++ library hiện tại)
                    // Tạm thời return nullptr (Hoặc bọc vào CompositeType tương ứng)
                    reqMethod.addLine("return nullptr; // TBD: Decode responseBody");
                    reqMethod.addMethodCloseStatement();
                    
                    // Thêm Async Request (Mục 4.3.2.11)
                    CompositeField adapterField = createField(file, "adapter", "std::shared_ptr<" + service + "Adapter>");
                    List<CompositeField> asyncArgs = StubUtils.concatenateArguments(opArgs, adapterField);
                    MethodWriter asyncReq = file.addMethodOpenStatement(false, false, false, "public", false, true, msgType, "async" + StubUtils.preCap(opName), asyncArgs, null);
                    asyncReq.addLine("return consumer->asyncRequest(" + opTypeVar + ", adapter, {" + generator.createArgNameOrNull(op.getArgTypes()) + "});");
                    asyncReq.addMethodCloseStatement();
                    break;
                case SUBMIT_OP:
                case INVOKE_OP:
                case PROGRESS_OP:
                case PUBSUB_OP:
                    // Tương tự, gọi các hàm submit, invoke, register của consumer
                    break;
            }
        }

        file.addClassCloseStatement();
        file.flush();
    }

    private CompositeField createField(ClassWriter file, String name, String type) {
        return generator.createCompositeElementsDetails(file, false, name,
                TypeUtils.createTypeReference(null, null, type, false), false, true, null);
    }

    private void addVirtualCallback(ClassWriter file, String methodName, List<CompositeField> args) throws IOException {
        file.addMethodOpenStatement(true, false, false, "public", false, false, null, methodName, args, null).addMethodCloseStatement();
    }
}