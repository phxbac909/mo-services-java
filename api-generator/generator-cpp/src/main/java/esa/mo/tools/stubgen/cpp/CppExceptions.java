package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.xsd.AreaType;
import esa.mo.xsd.ErrorDefinitionType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class CppExceptions {

    private final CppGeneratorLangs generator;

    public CppExceptions(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createAreaExceptions(File areaFolder, AreaType area) throws IOException {
        if (area.getErrors() != null && area.getErrors().getError() != null) {
            for (ErrorDefinitionType error : area.getErrors().getError()) {
                generateException(areaFolder, area.getName(), error);
            }
        }
    }

    // =========================================================================
    // HÀM HELPER COPY TỪ JAVA ĐỂ ĐẢM BẢO TÊN FILE GIỐNG 100%
    // =========================================================================
    public static String convertToCamelCase(String text) {
        // Is the Error in the old style? With all Upper Case and underscores?
        if (text.equals(text.toUpperCase())) {
            StringBuilder all = new StringBuilder();
            for (String part : text.split("_")) {
                if (part.isEmpty()) continue;
                StringBuilder camelCase = new StringBuilder(part.toLowerCase());
                camelCase.setCharAt(0, Character.toUpperCase(part.charAt(0)));
                all.append(camelCase.toString());
            }
            return all.toString();
        }
        return text.replace(" ", "").replace("_", "");
    }

    private void generateException(File folder, String areaName, ErrorDefinitionType error) throws IOException {
        String errorName = error.getName();

        // SỬA Ở ĐÂY: Dùng hàm convertToCamelCase để chuẩn hóa tên
        String className = convertToCamelCase(errorName) + "Exception";

        String errorCaps = errorName.toUpperCase().replace(" ", "_");

        ClassWriter file = generator.createClassFile(folder, className);
        file.addPackageStatement(areaName, null, null);

        CppClassWriter cppWriter = (CppClassWriter) file;
        cppWriter.addIncludeStatement(areaName + "Helper.hpp");
        cppWriter.addIncludeStatement("mo/mal/MOErrorException.hpp");

        String extendsClass = "::mo::mal::MOErrorException";
        file.addClassOpenStatement(className, false, false, extendsClass, null, "Exception class for " + errorName);

        CompositeField errNumField = generator.createCompositeElementsDetails(file, false, "errorNumber",
                TypeUtils.createTypeReference(null, null, "uint32_t", false), false, false, null);
        CompositeField extraInfoField = generator.createCompositeElementsDetails(file, false, "extraInformation",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<::mo::mal::Element>", false), false, true, null);

        file.addClassVariable(false, false, "public", errNumField, false, null);
        file.addClassVariable(false, false, "public", extraInfoField, false, null);

        MethodWriter method1 = file.addConstructor("public", className, null, null, null, "Default constructor", null);
        method1.addLine("this->errorNumber = " + areaName + "Helper::" + errorCaps + "_ERROR_NUMBER;");
        method1.addLine("this->extraInformation = nullptr;");
        method1.addMethodCloseStatement();

        MethodWriter method2 = file.addConstructor("public", className, Arrays.asList(extraInfoField), null, null, "Constructor with extra info", null);
        method2.addLine("this->errorNumber = " + areaName + "Helper::" + errorCaps + "_ERROR_NUMBER;");
        method2.addLine("this->extraInformation = extraInformation;");
        method2.addMethodCloseStatement();

        file.addClassCloseStatement();
        file.flush();
    }
}