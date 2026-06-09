package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.StdStrings;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.xsd.AreaType;
import esa.mo.xsd.CompositeType;
import esa.mo.xsd.ServiceType;
import esa.mo.xsd.TypeReference;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class CppCompositeClass {

    private final CppGeneratorLangs generator;

    public CppCompositeClass(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createCompositeClass(File folder, AreaType area, ServiceType service, CompositeType composite) throws IOException {
        String className = composite.getName();
        ClassWriter file = generator.createClassFile(folder, className);
        
        String parentClass = generator.convertToNamespace("mo::mal::Composite");
        TypeReference parentType = null;

        if (composite.getExtends() != null) {
            parentType = composite.getExtends().getType();
            if (!StdStrings.COMPOSITE.equals(parentType.getName())) {
                parentClass = generator.createElementType(parentType, true);
            }
        }

        String serviceName = (service == null) ? null : service.getName();
        file.addPackageStatement(area.getName(), serviceName, generator.getConfig().getStructureFolder());

        boolean abstractComposite = (composite.getShortFormPart() == null);
        file.addClassOpenStatement(className, !abstractComposite, abstractComposite, parentClass, null, composite.getComment());

        if (!abstractComposite) {
            generator.addTypeShortFormDetails(file, area, service, composite.getShortFormPart());
        }

        List<CompositeField> compElements = generator.createCompositeElementsList(file, composite);
        List<CompositeField> superCompElements = generator.createCompositeSuperElementsList(file, parentType);

        // Tạo Attributes
        for (CompositeField element : compElements) {
            file.addClassVariable(false, false, "private", element, false, null);
        }

        file.addConstructorDefault(className);
        createTypedConstructor(file, className, superCompElements, compElements);

        // Getters and Setters
        for (CompositeField element : compElements) {
            CppGeneratorLangs.addGetter(file, element, null);
            CppGeneratorLangs.addSetter(file, element, null);
        }

        // operator== (Mục 4.5.8.7)
        createEqualsOperator(file, className, parentClass, compElements);

        // Encode / Decode (Mục 4.5.8.9)
        createEncodeMethod(file, parentClass, compElements);
        createDecodeMethod(file, className, parentClass, compElements);

        file.addClassCloseStatement();
        file.flush();

        // Sinh List class kèm theo
        generator.createListClass(folder, area, service, className, abstractComposite, composite.getShortFormPart());
    }

    private void createTypedConstructor(ClassWriter file, String className, List<CompositeField> superCompElements, List<CompositeField> compElements) throws IOException {
        if (!compElements.isEmpty() || !superCompElements.isEmpty()) {
            List<CompositeField> allArgs = new LinkedList<>(superCompElements);
            allArgs.addAll(compElements);

            MethodWriter method = file.addConstructor("public", className, allArgs, superCompElements, null, "Constructor", null);
            for (CompositeField element : compElements) {
                method.addLine("this->" + element.getFieldName() + " = " + element.getFieldName() + ";");
            }
            method.addMethodCloseStatement();
        }
    }

    private void createEqualsOperator(ClassWriter file, String className, String parentClass, List<CompositeField> compElements) throws IOException {
        CompositeField boolType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(null, null, "bool", false), false, true, null);
        CompositeField argType = generator.createCompositeElementsDetails(file, false, "other",
                TypeUtils.createTypeReference(null, null, className, false), false, false, null);

        MethodWriter method = file.addMethodOpenStatement(false, true, false, "public", false, true, boolType, "operator==", Arrays.asList(argType), null);
        
        if (!parentClass.endsWith("Composite")) {
            method.addLine("if (!" + parentClass + "::operator==(other)) return false;");
        }
        
        for (CompositeField element : compElements) {
            String fName = element.getFieldName();
            method.addLine("if (this->" + fName + " != other." + fName + ") return false;"); // Nếu là shared_ptr, cần check deep compare trong C++ thực tế (tùy vào library)
        }
        method.addLine("return true;");
        method.addMethodCloseStatement();
    }

    private void createEncodeMethod(ClassWriter file, String parentClass, List<CompositeField> compElements) throws IOException {
        MethodWriter method = generator.encodeMethodOpen(file);
        if (!parentClass.endsWith("Composite")) {
            method.addLine(parentClass + "::encode(encoder);");
        }

        for (CompositeField element : compElements) {
            String call = element.getEncodeCall() != null ? element.getEncodeCall() : "Element";
            // SỬA: Dùng encoder-> thay vì encoder.
            method.addLine("encoder->encodeNullable" + call + "(this->" + element.getFieldName() + ");");
        }
        method.addMethodCloseStatement();
    }

    private void createDecodeMethod(ClassWriter file, String className, String parentClass, List<CompositeField> compElements) throws IOException {
        CompositeField elemType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(StdStrings.MAL, null, StdStrings.ELEMENT, false), true, true, null);

        MethodWriter method = generator.decodeMethodOpen(file, elemType);
        if (!parentClass.endsWith("Composite")) {
            method.addLine(parentClass + "::decode(decoder);");
        }

        for (CompositeField element : compElements) {
            String cppType = element.getTypeName();
            String call = element.getDecodeCall() != null ? element.getDecodeCall() : "Element";

            // Nếu là kiểu nguyên thủy (int32_t, bool), KHÔNG được dùng dynamic_pointer_cast
            boolean isNative = generator.isNativeType(cppType) && !generator.getNativeType(cppType).isObject();

            if (isNative) {
                // SỬA: Gọi thẳng hàm decode của kiểu nguyên thủy (VD: decodeNullableLong)
                method.addLine("this->" + element.getFieldName() + " = decoder->decodeNullable" + call + "();");
            } else {
                // SỬA: Dùng decoder-> và loại bỏ chữ std::shared_ptr<> bên trong cast để chuẩn cú pháp C++
                String castType = cppType.replace("std::shared_ptr<", "").replace(">", "");
                method.addLine("this->" + element.getFieldName() + " = std::dynamic_pointer_cast<" + castType + ">(decoder->decodeNullable" + call + "());");
            }
        }
        method.addLine("return std::make_shared<" + className + ">(*this);");
        method.addMethodCloseStatement();
    }
}