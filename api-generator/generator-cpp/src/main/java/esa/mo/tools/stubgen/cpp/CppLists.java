package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.StdStrings;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.xsd.AreaType;
import esa.mo.xsd.ServiceType;
import esa.mo.xsd.TypeReference;

import java.io.File;
import java.io.IOException;

public class CppLists {

    private final CppGeneratorLangs generator;

    public CppLists(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createHomogeneousListClass(File folder, AreaType area, ServiceType service, String srcTypeName, Integer shortFormPart) throws IOException {
        String listName = srcTypeName + "List";
        String fqSrcTypeName = generator.createElementType(area, service, srcTypeName);

        ClassWriter file = generator.createClassFile(folder, listName);
        file.addPackageStatement(area.getName(), service == null ? null : service.getName(), generator.getConfig().getStructureFolder());

        // Kế thừa std::vector và ElementList (Mục 4.5.7)
        String extendsStr = "std::vector<std::shared_ptr<" + fqSrcTypeName + ">>";
        String implementsStr = generator.convertToNamespace("mo::mal::ElementList");

        file.addClassOpenStatement(listName, false, false, extendsStr, implementsStr, "List class for " + srcTypeName + ".");
        generator.addTypeShortFormDetails(file, area, service, -shortFormPart);

        // Constructors
        file.addConstructorDefault(listName);

        MethodWriter method = file.addConstructor("public", listName,
                generator.createCompositeElementsDetails(file, false, "initialCapacity",
                        TypeUtils.createTypeReference(null, null, "int32_t", false), false, false, "The required initial capacity."),
                false, null, "Constructor that initialises the capacity of the list.", null);
        method.addLine("this->reserve(initialCapacity);");
        method.addMethodCloseStatement();

        // Encode / Decode method (Mục 4.5.7.4 và 4.5.7.5)
        CompositeField elemType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(StdStrings.MAL, null, StdStrings.ELEMENT, false), true, true, null);

        method = generator.encodeMethodOpen(file);
        method.addLine("auto listEncoder = encoder.createListEncoder(this->size());");
        method.addLine("for (const auto& element : *this) {");
        method.addLine("    listEncoder->encodeNullableElement(element);");
        method.addLine("}");
        method.addLine("listEncoder->close();");
        method.addMethodCloseStatement();

        method = generator.decodeMethodOpen(file, elemType);
        method.addLine("auto listDecoder = decoder.createListDecoder();");
        method.addLine("while (listDecoder->hasNext()) {");
        method.addLine("    auto element = std::dynamic_pointer_cast<" + fqSrcTypeName + ">(listDecoder->decodeNullableElement());");
        method.addLine("    this->push_back(element);");
        method.addLine("}");
        method.addLine("return std::make_shared<" + listName + ">(*this);"); // Copy this
        method.addMethodCloseStatement();

        file.addClassCloseStatement();
        file.flush();
    }
}