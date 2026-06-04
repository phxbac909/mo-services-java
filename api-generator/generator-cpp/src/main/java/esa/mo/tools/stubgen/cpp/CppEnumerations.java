package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.StdStrings;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.xsd.AreaType;
import esa.mo.xsd.EnumerationType;
import esa.mo.xsd.ServiceType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class CppEnumerations {

    private final CppGeneratorLangs generator;

    public CppEnumerations(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createEnumerationClass(File folder, AreaType area, ServiceType service, EnumerationType enumeration) throws IOException {
        String enumName = enumeration.getName();
        ClassWriter file = generator.createClassFile(folder, enumName);

        String serviceName = (service == null) ? null : service.getName();
        file.addPackageStatement(area.getName(), serviceName, generator.getConfig().getStructureFolder());

        // Mục 4.5.6.2: Class kế thừa từ mo::mal::Enumeration và phải là final
        String extendsClass = generator.convertToNamespace("mo::mal::Enumeration");
        file.addClassOpenStatement(enumName, true, false, extendsClass, null, "Enumeration class for " + enumName + ".");

        generator.addTypeShortFormDetails(file, area, service, enumeration.getShortFormPart());

        // Mục 4.5.6.3: Khai báo các items
        for (int i = 0; i < enumeration.getItem().size(); i++) {
            EnumerationType.Item item = enumeration.getItem().get(i);
            String value = item.getValue();

            // static const int _ITEM_INDEX = i;
            file.addStatement("    static const int _" + value + "_INDEX = " + i + ";");
            // static const uint32_t ITEM_NUM_VALUE = nvalue;
            file.addStatement("    static const uint32_t " + value + "_NUM_VALUE = " + item.getNvalue() + ";");
            // static const EnumClass ITEM; (Note: C++ static const object cần define trong .cpp)
            file.addStatement("    static const " + enumName + " " + value + ";");
        }

        // Tạo Constructor (Mục 3.3.5.2)
        MethodWriter constructor = file.addConstructor("public", enumName,
                generator.createCompositeElementsDetails(file, false, "ordinal",
                        TypeUtils.createTypeReference(null, null, "int32_t", false), false, false, "The ordinal value."),
                true, null, "Constructor", null);
        constructor.addMethodCloseStatement();

        // Các hàm: toString, fromOrdinal, fromString, fromNumericValue
        generateToString(file, enumeration);
        generateFromOrdinal(file, enumeration, enumName);
        generateFromString(file, enumeration, enumName);
        generateFromNumericValue(file, enumeration, enumName);

        file.addClassCloseStatement();
        file.flush();
    }

    private void generateToString(ClassWriter file, EnumerationType enumeration) throws IOException {
        CompositeField strType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(null, null, "std::string", false), false, true, null);

        MethodWriter method = file.addMethodOpenStatementOverride(strType, "toString", null, null);
        method.addLine("switch (getOrdinal()) {");
        for (EnumerationType.Item item : enumeration.getItem()) {
            method.addLine("    case _" + item.getValue() + "_INDEX:");
            method.addLine("        return \"" + item.getValue() + "\";");
        }
        method.addLine("    default:");
        method.addLine("        throw std::runtime_error(\"Unknown ordinal!\");");
        method.addLine("}");
        method.addMethodCloseStatement();
    }

    private void generateFromOrdinal(ClassWriter file, EnumerationType enumeration, String enumName) throws IOException {
        CompositeField retType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(null, null, enumName, false), false, true, null);
        CompositeField arg = generator.createCompositeElementsDetails(file, false, "ordinal",
                TypeUtils.createTypeReference(null, null, "int32_t", false), false, false, null);

        MethodWriter method = file.addMethodOpenStatement(false, false, true, "public", false, true, retType, "fromOrdinal", Arrays.asList(arg), null);
        method.addLine("switch (ordinal) {");
        for (EnumerationType.Item item : enumeration.getItem()) {
            method.addLine("    case _" + item.getValue() + "_INDEX:");
            method.addLine("        return " + item.getValue() + ";");
        }
        method.addLine("    default:");
        method.addLine("        throw std::runtime_error(\"Unknown ordinal!\");");
        method.addLine("}");
        method.addMethodCloseStatement();
    }

    private void generateFromString(ClassWriter file, EnumerationType enumeration, String enumName) throws IOException {
        // Tương tự fromOrdinal nhưng so sánh std::string
        CompositeField retType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(null, null, enumName, false), false, true, null);
        CompositeField arg = generator.createCompositeElementsDetails(file, false, "itemName",
                TypeUtils.createTypeReference(null, null, "std::string", false), false, false, null);

        MethodWriter method = file.addMethodOpenStatement(false, false, true, "public", false, true, retType, "fromString", Arrays.asList(arg), null);
        for (EnumerationType.Item item : enumeration.getItem()) {
            method.addLine("if (itemName == \"" + item.getValue() + "\") return " + item.getValue() + ";");
        }
        method.addLine("throw std::runtime_error(\"Unknown string!\");");
        method.addMethodCloseStatement();
    }

    private void generateFromNumericValue(ClassWriter file, EnumerationType enumeration, String enumName) throws IOException {
        CompositeField retType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(null, null, enumName, false), false, true, null);
        CompositeField arg = generator.createCompositeElementsDetails(file, false, "nvalue",
                TypeUtils.createTypeReference(null, null, "uint32_t", false), false, false, null);

        MethodWriter method = file.addMethodOpenStatement(false, false, true, "public", false, true, retType, "fromNumericValue", Arrays.asList(arg), null);
        method.addLine("switch (nvalue) {");
        for (EnumerationType.Item item : enumeration.getItem()) {
            method.addLine("    case " + item.getValue() + "_NUM_VALUE:");
            method.addLine("        return " + item.getValue() + ";");
        }
        method.addLine("    default:");
        method.addLine("        throw std::runtime_error(\"Unknown numeric value!\");");
        method.addLine("}");
        method.addMethodCloseStatement();
    }
}