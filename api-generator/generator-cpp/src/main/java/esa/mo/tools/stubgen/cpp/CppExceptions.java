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

    private void generateException(File folder, String areaName, ErrorDefinitionType error) throws IOException {
        String errorName = error.getName();
        // Xóa khoảng trắng, tạo CamelCase + "Exception"
        String className = errorName.replace(" ", "") + "Exception";
        String errorCaps = errorName.toUpperCase().replace(" ", "_");

        ClassWriter file = generator.createClassFile(folder, className);
        file.addPackageStatement(areaName, null, null);

        // Kế thừa mo::mal::MOErrorException
        String extendsClass = generator.convertToNamespace("mo::mal::MOErrorException");
        file.addClassOpenStatement(className, false, false, extendsClass, null, "Exception class for " + errorName);

        // Constructor mặc định
        MethodWriter method1 = file.addConstructor("public", className, null, null, null, "Default constructor", null);
        method1.addLine("this->errorNumber = " + areaName + "Helper::" + errorCaps + "_ERROR_NUMBER;");
        method1.addMethodCloseStatement();

        // Constructor có extra information
        CompositeField extraInfo = generator.createCompositeElementsDetails(file, false, "extraInformation",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::Element>", false), false, true, null);
        
        MethodWriter method2 = file.addConstructor("public", className, Arrays.asList(extraInfo), null, null, "Constructor with extra info", null);
        method2.addLine("this->errorNumber = " + areaName + "Helper::" + errorCaps + "_ERROR_NUMBER;");
        method2.addLine("this->extraInformation = extraInformation;");
        method2.addMethodCloseStatement();

        file.addClassCloseStatement();
        file.flush();
    }
}