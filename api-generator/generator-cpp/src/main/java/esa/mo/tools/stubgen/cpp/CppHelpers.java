package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs; // Import đúng class C++ mới
import esa.mo.tools.stubgen.specification.FieldInfo;
import esa.mo.tools.stubgen.specification.OperationSummary;
import esa.mo.tools.stubgen.specification.ServiceSummary;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.xsd.AreaType;
import esa.mo.xsd.ErrorDefinitionType;
import esa.mo.xsd.ServiceType;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CppHelpers {

    private final CppGeneratorLangs generator;

    public CppHelpers(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    // =========================================================================
    // 1. AREA HELPER
    // =========================================================================
    public void createAreaHelperClass(File areaFolder, AreaType area) throws IOException {
        String areaName = area.getName();
        String helperClassName = areaName + "Helper";
        String areaCaps = areaName.toUpperCase();

        ClassWriter file = generator.createClassFile(areaFolder, helperClassName);
        file.addPackageStatement(areaName, null, null);

        file.addClassOpenStatement(helperClassName, false, false, null, null, "Helper class for " + areaName + " area.");

        file.addStatement("public:");
        file.addStatement("    static const int32_t _" + areaCaps + "_AREA_NUMBER = " + area.getNumber() + ";");
        file.addStatement("    static const uint16_t " + areaCaps + "_AREA_NUMBER = " + area.getNumber() + ";");
        file.addStatement("    static const uint8_t " + areaCaps + "_AREA_VERSION = " + area.getVersion() + ";");
        file.addStatement("    static const char* const " + areaCaps + "_AREA_NAME;");
        file.addStatement("    static std::shared_ptr<mo::mal::MALArea> " + areaCaps + "_AREA;");

        if (area.getErrors() != null && !area.getErrors().getError().isEmpty()) {
            for (ErrorDefinitionType error : area.getErrors().getError()) {
                String errorCaps = error.getName().toUpperCase().replace(" ", "_");
                file.addStatement("    static const int32_t _" + errorCaps + "_ERROR_NUMBER = " + error.getNumber() + ";");
                file.addStatement("    static const uint32_t " + errorCaps + "_ERROR_NUMBER = " + error.getNumber() + ";");
            }
        }

        // LỖI 2 ĐƯỢC SỬA Ở ĐÂY: Dùng addSourceStatement để ghi vào file .cpp
        CppClassWriter cppWriter = (CppClassWriter) file;
        cppWriter.addSourceStatement("const char* const " + helperClassName + "::" + areaCaps + "_AREA_NAME = \"" + areaName + "\";");
        cppWriter.addSourceStatement("std::shared_ptr<mo::mal::MALArea> " + helperClassName + "::" + areaCaps + "_AREA = nullptr;");

        String regFactoryType = "std::shared_ptr<mo::mal::MALElementFactoryRegistry>";
        MethodWriter initMethod = file.addMethodOpenStatement(false, true, "public", false, false, null, "init",
                java.util.Arrays.asList(generator.createCompositeElementsDetails(file, false, "elementFactoryRegistry",
                        TypeUtils.createTypeReference(null, null, regFactoryType, false), false, false, null)), null);

        initMethod.addLine("if (" + areaCaps + "_AREA == nullptr) {");
        initMethod.addLine("    " + areaCaps + "_AREA = std::make_shared<mo::mal::MALArea>(");
        initMethod.addLine("        " + areaCaps + "_AREA_NUMBER,");
        initMethod.addLine("        std::make_shared<mo::mal::Identifier>(" + areaCaps + "_AREA_NAME),");
        initMethod.addLine("        " + areaCaps + "_AREA_VERSION);");
        initMethod.addLine("}");
        initMethod.addMethodCloseStatement();

        file.addClassCloseStatement();
        file.flush();
    }

    // =========================================================================
    // 2. SERVICE HELPER
    // =========================================================================
    public void createServiceHelperClass(File serviceFolder, String areaName, ServiceType service, ServiceSummary summary) throws IOException {
        String serviceName = service.getName();
        String helperClassName = serviceName + "Helper";
        String serviceCaps = serviceName.toUpperCase();

        ClassWriter file = generator.createClassFile(serviceFolder, helperClassName);
        file.addPackageStatement(areaName, serviceName, null);

        file.addClassOpenStatement(helperClassName, false, false, null, null, "Helper class for " + serviceName + " service.");

        file.addStatement("public:");
        file.addStatement("    static const int32_t _" + serviceCaps + "_SERVICE_NUMBER = " + service.getNumber() + ";");
        file.addStatement("    static const uint16_t " + serviceCaps + "_SERVICE_NUMBER = " + service.getNumber() + ";");
        file.addStatement("    static const char* const " + serviceCaps + "_SERVICE_NAME;");

        if (service.getErrors() != null && !service.getErrors().getError().isEmpty()) {
            for (ErrorDefinitionType error : service.getErrors().getError()) {
                String errorCaps = error.getName().toUpperCase().replace(" ", "_");
                file.addStatement("    static const int32_t _" + errorCaps + "_ERROR_NUMBER = " + error.getNumber() + ";");
                file.addStatement("    static const uint32_t " + errorCaps + "_ERROR_NUMBER = " + error.getNumber() + ";");
            }
        }

        file.addStatement("    static std::shared_ptr<mo::mal::MALService> " + serviceCaps + "_SERVICE;");

        for (OperationSummary op : summary.getOperations()) {
            String opCaps = op.getName().toUpperCase();
            file.addStatement("    static const int32_t _" + opCaps + "_OP_NUMBER = " + op.getNumber() + ";");
            file.addStatement("    static const uint16_t " + opCaps + "_OP_NUMBER = " + op.getNumber() + ";");

            // LỖI 1 ĐÃ ĐƯỢC GIẢI QUYẾT TỰ ĐỘNG BỞI CppGeneratorLangs
            String opTypeName = "mo::mal::" + generator.getOperationInstanceType(op);
            file.addStatement("    static std::shared_ptr<" + opTypeName + "> " + opCaps + "_OP;");
        }

        // LỖI 2 ĐƯỢC SỬA: Ép kiểu để xuất global definitions ra .cpp
        CppClassWriter cppWriter = (CppClassWriter) file;
        cppWriter.addSourceStatement("const char* const " + helperClassName + "::" + serviceCaps + "_SERVICE_NAME = \"" + serviceName + "\";");
        cppWriter.addSourceStatement("std::shared_ptr<mo::mal::MALService> " + helperClassName + "::" + serviceCaps + "_SERVICE = nullptr;");

        for (OperationSummary op : summary.getOperations()) {
            String opTypeName = "mo::mal::" + generator.getOperationInstanceType(op);
            cppWriter.addSourceStatement("std::shared_ptr<" + opTypeName + "> " + helperClassName + "::" + op.getName().toUpperCase() + "_OP = nullptr;");
        }

        String regFactoryType = "std::shared_ptr<mo::mal::MALElementFactoryRegistry>";
        MethodWriter initMethod = file.addMethodOpenStatement(false, true, "public", false, false, null, "init",
                java.util.Arrays.asList(generator.createCompositeElementsDetails(file, false, "elementFactoryRegistry",
                        TypeUtils.createTypeReference(null, null, regFactoryType, false), false, false, null)), null);

        initMethod.addLine("if (" + serviceCaps + "_SERVICE == nullptr) {");
        initMethod.addLine("    " + serviceCaps + "_SERVICE = std::make_shared<mo::mal::MALService>(");
        initMethod.addLine("        " + serviceCaps + "_SERVICE_NUMBER,");
        initMethod.addLine("        std::make_shared<mo::mal::Identifier>(" + serviceCaps + "_SERVICE_NAME));");

        for (OperationSummary op : summary.getOperations()) {
            String opCaps = op.getName().toUpperCase();
            String opTypeName = "mo::mal::" + generator.getOperationInstanceType(op);
            String capSet = (op.getSet() != null) ? op.getSet().toString() : "1";

            initMethod.addLine("    " + opCaps + "_OP = std::make_shared<" + opTypeName + ">(");
            initMethod.addLine("        " + opCaps + "_OP_NUMBER,");
            initMethod.addLine("        std::make_shared<mo::mal::Identifier>(\"" + op.getName() + "\"),");
            initMethod.addLine("        " + (op.getReplay() ? "true" : "false") + ",");
            initMethod.addLine("        " + capSet + ",");
            generateOperationStages(initMethod, op);
            initMethod.addLine("    );");
            initMethod.addLine("    " + serviceCaps + "_SERVICE->addOperation(" + opCaps + "_OP);");
        }

        initMethod.addLine("}");
        initMethod.addMethodCloseStatement();

        file.addClassCloseStatement();
        file.flush();
    }

    private void generateOperationStages(MethodWriter method, OperationSummary op) throws IOException {
        switch (op.getPattern()) {
            case SEND_OP:
            case SUBMIT_OP:
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(1, " + generateShortFormVector(op.getArgTypes()) + ", std::vector<int64_t>())");
                break;
            case REQUEST_OP:
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(1, " + generateShortFormVector(op.getArgTypes()) + ", std::vector<int64_t>()),");
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(2, " + generateShortFormVector(op.getRetTypes()) + ", std::vector<int64_t>())");
                break;
            case INVOKE_OP:
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(1, " + generateShortFormVector(op.getArgTypes()) + ", std::vector<int64_t>()),");
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(2, " + generateShortFormVector(op.getAckTypes()) + ", std::vector<int64_t>()),");
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(3, " + generateShortFormVector(op.getRetTypes()) + ", std::vector<int64_t>())");
                break;
            case PROGRESS_OP:
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(1, " + generateShortFormVector(op.getArgTypes()) + ", std::vector<int64_t>()),");
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(2, " + generateShortFormVector(op.getAckTypes()) + ", std::vector<int64_t>()),");
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(3, " + generateShortFormVector(op.getUpdateTypes()) + ", std::vector<int64_t>()),");
                method.addLine("        std::make_shared<mo::mal::MALOperationStage>(4, " + generateShortFormVector(op.getRetTypes()) + ", std::vector<int64_t>())");
                break;
            case PUBSUB_OP:
                method.addLine("        " + generateShortFormVector(op.getRetTypes()) + ", // Update Short Forms");
                method.addLine("        std::vector<int64_t>() // Last Update Short Forms");
                break;
        }
    }

    private String generateShortFormVector(List<FieldInfo> types) {
        if (types == null || types.isEmpty()) {
            return "std::vector<int64_t>()";
        }
        StringBuilder sb = new StringBuilder("std::vector<int64_t>{");
        boolean first = true;
        for (FieldInfo ti : types) {
            if (!first) sb.append(", ");
            String sf = ti.getMalShortFormField();
            if (sf == null || sf.isEmpty()) {
                sf = "0";
            } else {
                sf = generator.convertToNamespace(sf);
            }
            sb.append(sf);
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}