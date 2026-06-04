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
        String className = errorName.replace(" ", "") + "Exception";
        String errorCaps = errorName.toUpperCase().replace(" ", "_");

        ClassWriter file = generator.createClassFile(folder, className);
        file.addPackageStatement(areaName, null, null);

        CppClassWriter cppWriter = (CppClassWriter) file;

        // THÊM: Include file Area Helper để có biến ERROR_NUMBER
        cppWriter.addIncludeStatement(areaName + "Helper.hpp");

        // THÊM: C++ bắt buộc phải include header của class cha để có thể kế thừa
        cppWriter.addIncludeStatement("mo/mal/MOErrorException.hpp");

        // LỚP CHA: Dùng đường dẫn chuẩn ::mo::mal::MOErrorException
        String extendsClass = "::mo::mal::MOErrorException";
        file.addClassOpenStatement(className, false, false, extendsClass, null, "Exception class for " + errorName);

        CompositeField errNumField = generator.createCompositeElementsDetails(file, false, "errorNumber",
                TypeUtils.createTypeReference(null, null, "uint32_t", false), false, false, null);

        // SỬA: Sửa Element thành ::mo::mal::Element để chuẩn xác
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