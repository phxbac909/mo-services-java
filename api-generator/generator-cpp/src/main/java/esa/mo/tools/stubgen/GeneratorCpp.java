/* ----------------------------------------------------------------------------
 * Copyright (C) 2024      European Space Agency
 * System                : CCSDS MO Service Stub Generator (C++)
 * ----------------------------------------------------------------------------
 */
package esa.mo.tools.stubgen;

import esa.mo.tools.stubgen.cpp.CppClassWriter;
import esa.mo.tools.stubgen.cpp.CppCompositeFields;
import esa.mo.tools.stubgen.cpp.CppLists;
import esa.mo.tools.stubgen.cpp.CppPublisher;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.NativeTypeDetails;
import esa.mo.tools.stubgen.specification.StdStrings;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.InterfaceWriter;
import esa.mo.tools.stubgen.writers.LanguageWriter;
import esa.mo.tools.stubgen.writers.TargetWriter;
import esa.mo.xsd.AreaType;
import esa.mo.xsd.ServiceType;
import esa.mo.xsd.TypeReference;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class GeneratorCpp extends CppGeneratorLangs {

    public static final String CPP_HEADER_EXT = "hpp";
    public static final String CPP_SOURCE_EXT = "cpp";

    public GeneratorCpp(org.apache.maven.plugin.logging.Log logger) {
        super(logger,
                new GeneratorConfiguration(
                        "mo::mal::", "structures", "factory", "body", "::", "nullptr",
                        "MALSendOperation", "MALSubmitOperation", "MALRequestOperation",
                        "MALInvokeOperation", "MALProgressOperation", "MALPubSubOperation"));
    }

    @Override
    public String getShortName() { return "CPP"; }

    @Override
    public String getDescription() { return "Generates a C++ (C++11) language mapping based on CCSDS 523.2-M-1."; }

    @Override
    public void init(String destinationFolderName, boolean generateStructures, boolean generateCOM,
                     Map<String, String> packageBindings, Map<String, String> extraProperties) throws IOException {
        super.init(destinationFolderName, generateStructures, generateCOM, packageBindings, extraProperties);

        addAttributeType(StdStrings.MAL, StdStrings.BLOB, false, "Blob", "nullptr");
        addAttributeType(StdStrings.MAL, StdStrings.BOOLEAN, true, "bool", "false");
        addAttributeType(StdStrings.MAL, StdStrings.DOUBLE, true, "double", "0.0");
        addAttributeType(StdStrings.MAL, StdStrings.DURATION, true, "double", "0.0");
        addAttributeType(StdStrings.MAL, StdStrings.FLOAT, true, "float", "0.0f");
        addAttributeType(StdStrings.MAL, StdStrings.INTEGER, true, "int32_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.IDENTIFIER, false, "std::string", "\"\"");
        addAttributeType(StdStrings.MAL, StdStrings.LONG, true, "int64_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.OCTET, true, "int8_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.SHORT, true, "int16_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.UINTEGER, true, "uint32_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.ULONG, true, "uint64_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.UOCTET, true, "uint8_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.USHORT, true, "uint16_t", "0");
        addAttributeType(StdStrings.MAL, StdStrings.STRING, false, "std::string", "\"\"");
        addAttributeType(StdStrings.MAL, StdStrings.TIME, false, "MALTimeData", "nullptr");
        addAttributeType(StdStrings.MAL, StdStrings.FINETIME, false, "MALFineTimeData", "nullptr");
        addAttributeType(StdStrings.MAL, StdStrings.URI, false, "std::string", "\"\"");
        addAttributeType(StdStrings.MAL, StdStrings.OBJECTREF, false, "ObjectRef", "nullptr");

        super.addNativeType("bool", new NativeTypeDetails("bool", false, false, null));
        super.addNativeType("std::string", new NativeTypeDetails("std::string", false, false, "<string>"));
        super.addNativeType("int8_t", new NativeTypeDetails("int8_t", false, false, "<cstdint>"));
        super.addNativeType("int16_t", new NativeTypeDetails("int16_t", false, false, "<cstdint>"));
        super.addNativeType("int32_t", new NativeTypeDetails("int32_t", false, false, "<cstdint>"));
        super.addNativeType("int64_t", new NativeTypeDetails("int64_t", false, false, "<cstdint>"));
        super.addNativeType("uint8_t", new NativeTypeDetails("uint8_t", false, false, "<cstdint>"));
        super.addNativeType("uint16_t", new NativeTypeDetails("uint16_t", false, false, "<cstdint>"));
        super.addNativeType("uint32_t", new NativeTypeDetails("uint32_t", false, false, "<cstdint>"));
        super.addNativeType("uint64_t", new NativeTypeDetails("uint64_t", false, false, "<cstdint>"));
        super.addNativeType("float", new NativeTypeDetails("float", false, false, null));
        super.addNativeType("double", new NativeTypeDetails("double", false, false, null));
        super.addNativeType("Vector", new NativeTypeDetails("std::vector", true, true, "<vector>"));
        super.addNativeType("Map", new NativeTypeDetails("std::map", true, true, "<map>"));
    }

    @Override
    public String convertToNamespace(String targetType) {
        if (targetType != null) { targetType = targetType.replace(".", "::"); }
        return targetType;
    }

    @Override
    public CompositeField createCompositeElementsDetails(TargetWriter file, boolean checkType, String fieldName,
                                                         esa.mo.xsd.TypeReference elementType, boolean isStructure,
                                                         boolean canBeNull, String comment) {
        CppCompositeFields fields = new CppCompositeFields(this);
        return fields.createCompositeElementsDetails(checkType, fieldName, elementType, isStructure, canBeNull, comment);
    }

    @Override
    public CompositeField createCompositeElementsDetails(Object file, boolean checkType, String fieldName, TypeReference elementType, boolean isStructure, boolean canBeNull, String comment) {
        return null;
    }

    @Override
    protected String malStringAsElement(LanguageWriter file) {
        return createElementType(StdStrings.MAL, null, "String");
    }

    @Override
    public ClassWriter createClassFile(File folder, String className) throws IOException {
        return new CppClassWriter(folder, className, this);
    }

    @Override
    public ClassWriter createClassFile(String destinationFolderName, String className) throws IOException {
        return new CppClassWriter(destinationFolderName, className, this);
    }

    @Override
    public InterfaceWriter createInterfaceFile(File folder, String className) throws IOException {
        return new CppClassWriter(folder, className, this);
    }

    @Override
    public void createListClass(File folder, AreaType area, ServiceType service, String srcTypeName, boolean isAbstract, Integer shortFormPart) throws IOException {
        CppLists cppLists = new CppLists(this);
        if (!isAbstract) { cppLists.createHomogeneousListClass(folder, area, service, srcTypeName, shortFormPart); }
    }


    @Override
    protected void addShortForm(ClassWriter file, long sf) throws IOException {
        file.addStatement("    static const int64_t SHORT_FORM = " + sf + "L;");
        long typeShortForm = sf & 0xFFFFFFFFL;
        file.addStatement("    static const int32_t TYPE_SHORT_FORM = " + typeShortForm + ";");
    }

    @Override
    protected void createRequiredPublisher(String destinationFolderName, String fqPublisherName, RequiredPublisher op) throws IOException {
        CppPublisher publisher = new CppPublisher(this);
        publisher.createPublisherClass(destinationFolderName, fqPublisherName, op);
    }
}