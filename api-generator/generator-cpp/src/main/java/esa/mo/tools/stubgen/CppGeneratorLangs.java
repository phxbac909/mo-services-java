/* ----------------------------------------------------------------------------
 * Copyright (C) 2024      European Space Agency
 *                         European Space Operations Centre
 *                         Darmstadt
 *                         Germany
 * ----------------------------------------------------------------------------
 * System                : CCSDS MO Service Stub Generator (C++)
 * ----------------------------------------------------------------------------
 * Licensed under the European Space Agency Public License, Version 2.0
 * ----------------------------------------------------------------------------
 */
package esa.mo.tools.stubgen;

import esa.mo.tools.stubgen.cpp.*;
import esa.mo.tools.stubgen.specification.*;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.InterfaceWriter;
import esa.mo.tools.stubgen.writers.LanguageWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.tools.stubgen.writers.TargetWriter;
import esa.mo.xsd.*;
import esa.mo.xsd.util.XmlSpecification;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import org.apache.maven.plugin.logging.Log;

/**
 * Main generator flow for C++ language.
 * Contains all helper methods required by C++ components, adapted from the original Java generator.
 */
public abstract class CppGeneratorLangs extends GeneratorBase {

    public static final int AREA_BIT_SHIFT = 48;
    public static final int SERVICE_BIT_SHIFT = 32;
    public static final int VERSION_BIT_SHIFT = 24;

    public static final String CONSUMER_FOLDER = "consumer";
    public static final String PROVIDER_FOLDER = "provider";
    public static final String TRANSPORT_FOLDER = "transport";

    private final Map<TypeKey, ModelObjectType> comObjectMap = new HashMap<>();
    private final Map<String, MultiReturnType> multiReturnTypeMap = new HashMap<>();
    private final Map<String, String> reservedWordsMap = new HashMap<>();
    private final Map<String, RequiredPublisher> requiredPublishers = new HashMap<>();

    public boolean supportsToString;
    public boolean supportsEquals;
    public boolean requiresDefaultConstructors;
    public boolean supportsToValue;

    private boolean generateStructures;
    protected final Log logger;

    public CppGeneratorLangs(Log logger, boolean supportsToString, boolean supportsEquals,
                             boolean supportsToValue, boolean supportsAsync,
                             boolean requiresDefaultConstructors, GeneratorConfiguration config) {
        super(config);
        this.logger = logger;
        this.supportsToString = supportsToString;
        this.supportsEquals = supportsEquals;
        this.supportsToValue = supportsToValue;
        this.requiresDefaultConstructors = requiresDefaultConstructors;
    }

    public CppGeneratorLangs(Log logger, GeneratorConfiguration config) {
        this(logger, true, true, false, true, true, config);
    }

    @Override
    public void init(String destinationFolderName, boolean generateStructures, boolean generateCOM,
                     Map<String, String> packageBindings, Map<String, String> extraProperties) throws IOException {
        super.init(destinationFolderName, generateStructures, generateCOM, packageBindings, extraProperties);
        this.generateStructures = generateStructures;
    }

    @Override
    public void loadXML(XmlSpecification xml) throws IOException, JAXBException {
        super.loadXML(xml);
        SpecificationType spec = xml.getSpecType();

        for (AreaType area : spec.getArea()) {
            for (ServiceType service : area.getService()) {
                if (service instanceof ExtendedServiceType) {
                    ExtendedServiceType eService = (ExtendedServiceType) service;
                    SupportedFeatures features = eService.getFeatures();
                    if (features == null) continue;

                    if (features.getObjects() != null) {
                        for (ModelObjectType obj : features.getObjects().getObject()) {
                            String name = String.valueOf(obj.getNumber());
                            TypeKey key = new TypeKey(area.getName(), service.getName(), name);
                            comObjectMap.put(key, obj);
                        }
                    }
                    if (features.getEvents() != null) {
                        for (ModelObjectType obj : features.getEvents().getEvent()) {
                            String name = String.valueOf(obj.getNumber());
                            TypeKey key = new TypeKey(area.getName(), service.getName(), name);
                            comObjectMap.put(key, obj);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void generate(String destinationFolderName, XmlSpecification xml, JAXBElement rootNode) throws IOException, JAXBException {
        long totalTime = System.currentTimeMillis();
        SpecificationType spec = xml.getSpecType();

        for (AreaType area : spec.getArea()) {
            long timestamp = System.currentTimeMillis();
            processArea(destinationFolderName, area, requiredPublishers);
            timestamp = System.currentTimeMillis() - timestamp;
            logger.info("-----------");
            logger.info("Processed " + area.getName() + " area in " + timestamp + " ms");
            logger.info("-----------");
        }

        for (Map.Entry<String, MultiReturnType> entry : multiReturnTypeMap.entrySet()) {
            String string = entry.getKey();
            MultiReturnType rt = entry.getValue();
            createMultiReturnType(destinationFolderName, string, rt);
        }

        logger.info("-----------");
        totalTime = System.currentTimeMillis() - totalTime;
        logger.info("Processed all Areas in " + totalTime + " ms");
    }

    @Override
    public void close(String destinationFolderName) throws IOException {
        for (Map.Entry<String, RequiredPublisher> ele : requiredPublishers.entrySet()) {
            String string = ele.getKey();
            if (!string.contains(".com.com.provider.") || generateCOM()) {
                createRequiredPublisher(destinationFolderName, string, ele.getValue());
            }
        }
    }

    protected void processArea(String destinationFolderName, AreaType area, Map<String, RequiredPublisher> requiredPublishers) throws IOException {
        if (!area.getName().equalsIgnoreCase(StdStrings.COM) || generateCOM()) {
            logger.info("Processing area: " + area.getName());

            String packageFolder = getConfig().getAreaPackage(area.getName()).replace("::", "/");
            File destinationFolder = StubUtils.createFolder(new File(destinationFolderName), packageFolder);
            final File areaFolder = StubUtils.createFolder(destinationFolder, area.getName().toLowerCase());

            ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();
            Thread t1 = new Thread(() -> {
                for (ServiceType service : area.getService()) {
                    try {
                        processService(areaFolder, area, service, requiredPublishers);
                    } catch (IOException ex) {
                        errors.add(ex);
                    }
                }
            });
            t1.start();

            CppHelpers helper = new CppHelpers(this);
            helper.createAreaHelperClass(areaFolder, area);

            CppExceptions exceptions = new CppExceptions(this);
            exceptions.createAreaExceptions(areaFolder, area);

            if (generateStructures && area.getDataTypes() != null && !area.getDataTypes().getFundamentalOrAttributeOrComposite().isEmpty()) {
                File structureFolder = StubUtils.createFolder(areaFolder, getConfig().getStructureFolder());

                for (Object oType : area.getDataTypes().getFundamentalOrAttributeOrComposite()) {
                    if (oType instanceof CompositeType) {
                        CompositeType compType = (CompositeType) oType;
                        logger.info(" > Creating C++ Composite class: " + compType.getName());
                        CppCompositeClass compositeClass = new CppCompositeClass(this);
                        compositeClass.createCompositeClass(structureFolder, area, null, compType);

                    } else if (oType instanceof EnumerationType) {
                        EnumerationType enumType = (EnumerationType) oType;
                        logger.info(" > Creating C++ Enumeration class: " + enumType.getName());
                        CppEnumerations enumerations = new CppEnumerations(this);
                        enumerations.createEnumerationClass(structureFolder, area, null, enumType);
                    } else if (oType instanceof AttributeType) {
                        String aName = ((AttributeType) oType).getName();
                        createListClass(structureFolder, area, null, aName, false, ((AttributeType) oType).getShortFormPart());
                    }
                }
            }

            try {
                t1.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(CppGeneratorLangs.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (!errors.isEmpty()) {
                throw new IOException(errors.poll());
            }
        }
    }
//
//    protected void processService(File areaFolder, AreaType area, ServiceType service, Map<String, RequiredPublisher> requiredPublishers) throws IOException {
//        logger.info("Processing service: " + service.getName());
//        File serviceFolder = StubUtils.createFolder(areaFolder, service.getName().toLowerCase());
//
//        ServiceSummary summary = createOperationElementList(service);
//
//        CppHelpers helper = new CppHelpers(this);
//        helper.createServiceHelperClass(serviceFolder, area.getName(), service, summary);
//
//        CppServiceInfo serviceInfo = new CppServiceInfo(this);
//        serviceInfo.createServiceInfoClass(serviceFolder, area, service, summary);
//
//        CppConsumer consumer = new CppConsumer(this);
//        consumer.createServiceConsumerClasses(serviceFolder, area.getName(), service.getName(), summary);
//
//        CppProvider provider = new CppProvider(this);
//        provider.createServiceProviderClasses(serviceFolder, area.getName(), service.getName(), summary, requiredPublishers);
//
//        if (generateStructures && service.getDataTypes() != null && !service.getDataTypes().getCompositeOrEnumeration().isEmpty()) {
//            File structureFolder = StubUtils.createFolder(serviceFolder, getConfig().getStructureFolder());
//
//            for (Object oType : service.getDataTypes().getCompositeOrEnumeration()) {
//                if (oType instanceof EnumerationType) {
//                    CppEnumerations enumerations = new CppEnumerations(this);
//                    enumerations.createEnumerationClass(structureFolder, area, service, (EnumerationType) oType);
//                } else if (oType instanceof CompositeType) {
//                    CppCompositeClass compositeClass = new CppCompositeClass(this);
//                    compositeClass.createCompositeClass(structureFolder, area, service, (CompositeType) oType);
//                }
//            }
//        }
//    }

    public abstract CompositeField createCompositeElementsDetails(TargetWriter file, boolean checkType, String fieldName,
                                                                  esa.mo.xsd.TypeReference elementType, boolean isStructure,
                                                                  boolean canBeNull, String comment);
    protected void processService(File areaFolder, AreaType area, ServiceType service, Map<String, RequiredPublisher> requiredPublishers) throws IOException {
        logger.info("Processing service: " + service.getName());
        File serviceFolder = StubUtils.createFolder(areaFolder, service.getName().toLowerCase());

        ServiceSummary summary = createOperationElementList(service);

        CppHelpers helper = new CppHelpers(this);
        helper.createServiceHelperClass(serviceFolder, area.getName(), service, summary);

        // XÓA COMMENT MẤY DÒNG NÀY ĐỂ NÓ CHẠY
        CppServiceInfo serviceInfo = new CppServiceInfo(this);
        serviceInfo.createServiceInfoClass(serviceFolder, area, service, summary);

        CppConsumer consumer = new CppConsumer(this);
        consumer.createServiceConsumerClasses(serviceFolder, area.getName(), service.getName(), summary);

        CppProvider provider = new CppProvider(this);
        provider.createServiceProviderClasses(serviceFolder, area.getName(), service.getName(), summary, requiredPublishers);
    }

    // =========================================================================
    // ABSTRACT METHODS (Tobe implemented by GeneratorCpp)
    // =========================================================================

    public abstract ClassWriter createClassFile(File folder, String className) throws IOException;
    public abstract ClassWriter createClassFile(String destinationFolderName, String className) throws IOException;
    public abstract InterfaceWriter createInterfaceFile(File folder, String className) throws IOException;
    protected abstract void addShortForm(ClassWriter file, long sf) throws IOException;
    protected abstract void createRequiredPublisher(String destinationFolderName, String fqPublisherName, RequiredPublisher op) throws IOException;
    public abstract void createListClass(File folder, AreaType area, ServiceType service, String srcTypeName, boolean isAbstract, Integer shortFormPart) throws IOException;
    protected abstract String malStringAsElement(LanguageWriter file);

    // =========================================================================
    // HELPER METHODS TỪ BẢN JAVA - ĐÃ ĐƯỢC CHỈNH SỬA CHO C++
    // =========================================================================

    public List<CompositeField> createOperationArguments(GeneratorConfiguration config, LanguageWriter file, List<FieldInfo> opArgs) {
        if (opArgs == null) return new LinkedList<>();
        List<CompositeField> outputArgs = new LinkedList<>();

        for (int i = 0; i < opArgs.size(); i++) {
            FieldInfo ti = opArgs.get(i);
            esa.mo.xsd.TypeReference tir = ti.getSourceType();
            String argName = ti.getFieldName();

            if (argName == null) {
                String shortName = TypeUtils.shortTypeName(config.getNamingSeparator(), ti.getTargetType());
                shortName = shortName.replace(">", "_");
                argName = "_" + shortName + i;
            }

            String cmt = argName + " Argument number " + i + " as defined by the service operation";
            if (ti.getFieldName() != null && ti.getFieldComment() != null) {
                cmt = ti.getFieldComment();
            }

            CompositeField argType = createCompositeElementsDetails(file, true, argName, tir, true, true, cmt);
            outputArgs.add(argType);
        }
        return outputArgs;
    }

    public CompositeField createOperationReturnType(LanguageWriter file, String area, String service, OperationSummary op) {
        switch (op.getPattern()) {
            case REQUEST_OP: {
                if (op.getRetTypes() != null) {
                    CompositeField ret = createReturnType(file, area, service, op.getName(), "Response", op.getRetTypes());
                    return createReturnReference(ret);
                }
                break;
            }
            case INVOKE_OP:
            case PROGRESS_OP: {
                if (op.getAckTypes() != null && !op.getAckTypes().isEmpty()) {
                    CompositeField ret = createReturnType(file, area, service, op.getName(), "Ack", op.getAckTypes());
                    return createReturnReference(ret);
                }
                break;
            }
        }
        return null;
    }

    protected CompositeField createReturnType(LanguageWriter file, String area, String service, String opName, String messageType, List<FieldInfo> returnTypes) {
        if (returnTypes == null || returnTypes.isEmpty()) return null;

        if (returnTypes.size() == 1) {
            return createCompositeElementsDetails(file, false, "return", returnTypes.get(0).getSourceType(), true, true, null);
        }

        String shortName = StubUtils.preCap(opName) + messageType;
        // Chuyển sang C++ namespace
        String rt = getConfig().getAreaPackage(area) + area.toLowerCase() + "::" + service.toLowerCase() + "::" + getConfig().getBodyFolder() + "::" + shortName;

        if (!multiReturnTypeMap.containsKey(rt)) {
            multiReturnTypeMap.put(rt, new MultiReturnType(rt, area, service, shortName, returnTypes));
        }

        return createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(area.toLowerCase(), service.toLowerCase() + "::" + getConfig().getBodyFolder(), shortName, false),
                false, true, null);
    }

    public String createArgNameOrNull(List<FieldInfo> typeNames) {
        if (typeNames == null || typeNames.isEmpty()) {
            return getConfig().getNullValue();
        }

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < typeNames.size(); i++) {
            if (i > 0) buf.append(", ");
            String argName = typeNames.get(i).getFieldName();
            if (argName == null) {
                argName = "_" + TypeUtils.shortTypeName(getConfig().getNamingSeparator(), typeNames.get(i).getTargetType()) + i;
            }
            buf.append(argName);
        }
        return buf.toString().replaceAll("<", "_").replaceAll(">", "_");
    }

    public String createAdapterMethodsArgs(List<FieldInfo> typeInfos, String argNamePrefix, boolean precedingArgs, boolean moreArgs) {
        if (typeInfos == null) return "";
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < typeInfos.size(); i++) {
            FieldInfo ti = typeInfos.get(i);
            boolean morePrecedingArgs = precedingArgs || (i > 0);
            boolean evenMoreArgs = moreArgs && i == (typeInfos.size() - 1);
            buf.append(createAdapterMethodsArgs(ti, argNamePrefix, i, morePrecedingArgs, evenMoreArgs));
        }
        return buf.toString();
    }

    public String createAdapterMethodsArgs(FieldInfo typeInfo, String argName, int argIndex, boolean precedingArgs, boolean moreArgs) {
        String retStr = "";
        if (typeInfo.getTargetType() != null && !StdStrings.VOID.equals(typeInfo.getTargetType())) {
            if (precedingArgs) retStr = ",\n                ";

            // C++: Dùng dynamic_pointer_cast để cast kiểu an toàn từ Element
            String cast = typeInfo.getTargetType().replace(".ElementList", "::HeterogeneousList").replace(".", "::");
            String av = argName + "->getBodyElement(" + argIndex + ", nullptr)";
            retStr += "std::dynamic_pointer_cast<" + cast + ">(" + av + ")";

            if (moreArgs) retStr += ",\n                ";
        }
        return retStr;
    }

    public String getOperationInstanceType(OperationSummary op) {
        switch (op.getPattern()) {
            case SEND_OP: return getConfig().getSendOperationType();
            case SUBMIT_OP: return getConfig().getSubmitOperationType();
            case REQUEST_OP: return getConfig().getRequestOperationType();
            case INVOKE_OP: return getConfig().getInvokeOperationType();
            case PROGRESS_OP: return getConfig().getProgressOperationType();
            case PUBSUB_OP: return getConfig().getPubsubOperationType();
        }
        return null;
    }

    public static String createConsumerPatternCall(OperationSummary op) {
        switch (op.getPattern()) {
            case SEND_OP: return "send";
            case SUBMIT_OP: return "submit";
            case REQUEST_OP: return "request";
            case INVOKE_OP: return "invoke";
            case PROGRESS_OP: return "progress";
        }
        return null;
    }

    public String checkForReservedWords(String arg) {
        if (arg == null) return arg;
        String replacementWord = reservedWordsMap.get(arg);
        return (replacementWord != null) ? replacementWord : arg;
    }

    protected CompositeField createServiceProviderSkeletonSendHandler(ClassWriter file, String argumentName, String argumentComment) {
        return createCompositeElementsDetails(file, false, argumentName,
                TypeUtils.createTypeReference(StdStrings.MAL, PROVIDER_FOLDER, StdStrings.MALINTERACTION, false),
                false, true, argumentComment);
    }

    protected String createProviderSkeletonHandlerSwitch() {
        return "interaction->getMessageHeader()->getOperation()->getValue()";
    }

    public String createOperationArgReturn(LanguageWriter file, MethodWriter method, FieldInfo typeInfo, String argName, int argIndex) throws IOException {
        if (typeInfo.getTargetType() != null && !StdStrings.VOID.equals(typeInfo.getTargetType())) {
            String tv = argName + argIndex;
            String cast = typeInfo.getTargetType().replace(".ElementList", "::HeterogeneousList").replace(".", "::");
            String av = argName + "->getBodyElement(" + argIndex + ", nullptr)";
            method.addLine("auto " + tv + " = std::dynamic_pointer_cast<" + cast + ">(" + av + ");");
            return tv;
        }
        return "";
    }

    public CompositeField createReturnReference(CompositeField targetType) {
        return targetType;
    }

    public void addTypeShortFormDetails(ClassWriter file, AreaType area, ServiceType service, long sf) throws IOException {
        long asf = ((long) area.getNumber()) << AREA_BIT_SHIFT;
        asf += ((long) area.getVersion()) << VERSION_BIT_SHIFT;
        if (service != null) asf += ((long) service.getNumber()) << SERVICE_BIT_SHIFT;
        if (sf >= 0) {
            asf += sf;
        } else {
            asf += Long.parseLong(Integer.toHexString((int) sf).toUpperCase().substring(2), 16);
        }
        addShortForm(file, asf);
        addTypeId(file, asf);
    }

    protected void addTypeId(ClassWriter file, long sf) throws IOException {
        CompositeField var = createCompositeElementsDetails(file, false, "TYPE_ID",
                TypeUtils.createTypeReference(StdStrings.MAL, null, "TypeId", false), true, false, "The TypeId of this Element.");
        file.addClassVariable(true, true, "public", var, false, "(SHORT_FORM)");
    }

    public void addTypeIdGetterMethod(ClassWriter file, AreaType area, ServiceType service) throws IOException {
        CompositeField typeIdType = createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(StdStrings.MAL, null, "TypeId", false), true, true, null);
        MethodWriter method = file.addMethodOpenStatementOverride(typeIdType, "getTypeId", null, null);
        method.addLine("return TYPE_ID;");
        method.addMethodCloseStatement();
    }

    public static void addGetter(ClassWriter file, CompositeField element, String backwardCompatibility) throws IOException {
        String attributeName = element.getFieldName();
        String getOpName = (backwardCompatibility == null) ? StubUtils.preCap(attributeName) : backwardCompatibility;
        MethodWriter method = file.addMethodOpenStatement(false, true, true, false, "public", false, true, element,
                "get" + getOpName, null, null, "Returns the field " + attributeName, "The field " + attributeName, null, false);
        method.addLine("return this->" + attributeName + ";");
        method.addMethodCloseStatement();
    }

    public static void addSetter(ClassWriter file, CompositeField element, String backwardCompatibility) throws IOException {
        String attributeName = element.getFieldName();
        String getOpName = (backwardCompatibility == null) ? StubUtils.preCap(attributeName) : backwardCompatibility;
        if (StdStrings.BOOLEAN.equals(element.getTypeName()) && getOpName.startsWith("Is")) {
            getOpName = getOpName.substring(2);
        }
        CompositeField arg = new CompositeField(element, "__newValue", "The new value.");
        MethodWriter method = file.addMethodOpenStatement(false, true, false, false, "public", false, true, null,
                "set" + getOpName, java.util.Arrays.asList(arg), null, "Sets the field " + attributeName, null, null, false);
        method.addLine("this->" + attributeName + " = __newValue;");
        method.addMethodCloseStatement();
    }

    public MethodWriter encodeMethodOpen(ClassWriter file) throws IOException {
        String throwsMALException = createElementType(StdStrings.MAL, null, null, StdStrings.MALEXCEPTION);
        CompositeField fld = createCompositeElementsDetails(file, false, "encoder",
                TypeUtils.createTypeReference(StdStrings.MAL, null, "MALEncoder", false), false, true, "The encoder to use for encoding.");
        return file.addMethodOpenStatementOverride(null, "encode", Arrays.asList(fld), throwsMALException);
    }

    public MethodWriter decodeMethodOpen(ClassWriter file, CompositeField returnType) throws IOException {
        String throwsMALException = createElementType(StdStrings.MAL, null, null, StdStrings.MALEXCEPTION);
        CompositeField fld = createCompositeElementsDetails(file, false, "decoder",
                TypeUtils.createTypeReference(StdStrings.MAL, null, "MALDecoder", false), false, true, "The decoder to use for decoding.");
        return file.addMethodOpenStatementOverride(returnType, "decode", Arrays.asList(fld), throwsMALException);
    }

    public String getReferenceShortForm(AnyTypeReference ref) {
        String rv = null;
        if (ref != null && ref.getAny() != null && !ref.getAny().isEmpty()) {
            List<FieldInfo> refs = TypeUtils.convertTypeReferences(this, TypeUtils.getTypeListViaXSDAny(ref.getAny()));
            rv = refs.get(0).getMalShortFormField();
        }
        return rv;
    }

    public String getReferenceShortForm(TargetWriter file, OptionalObjectReference oor) {
        if (oor == null || oor.getObjectType() == null) return null;
        ObjectReference any = oor.getObjectType();
        String service = any.getService();
        TypeKey key = new TypeKey(any.getArea(), service, String.valueOf(any.getNumber()));
        if (!comObjectMap.containsKey(key)) return null;
        ModelObjectType refObj = comObjectMap.get(key);
        return convertToNamespace(createElementType(any.getArea(), service, null, service + "ServiceInfo") + "::" + refObj.getName().toUpperCase() + "_OBJECT_TYPE");
    }
    protected final void createMultiReturnType(String destinationFolderName, String returnTypeFqName, MultiReturnType returnTypeInfo) throws IOException {
        logger.info(" > Creating C++ multiple return class: " + returnTypeFqName);

        // Trong C++, tên thư mục sẽ dùng "/" thay vì "::" hoặc "."
        String filePath = returnTypeFqName.replace("::", "/").replace(".", "/");

        ClassWriter file = createClassFile(destinationFolderName, filePath);

        // Thêm namespace
        file.addPackageStatement(returnTypeInfo.getArea(), returnTypeInfo.getService(), getConfig().getBodyFolder());

        // Kế thừa Element để có thể truyền được qua MAL framework
        String baseClass = convertToNamespace("mo::mal::Element");
        file.addClassOpenStatement(returnTypeInfo.getShortName(), true, false, baseClass, null,
                "Multi body return class for " + returnTypeInfo.getShortName() + ".");

        List<CompositeField> argsList = createOperationArguments(getConfig(), file, returnTypeInfo.getReturnTypes());

        // Tạo Attributes (Biến private)
        for (int i = 0; i < argsList.size(); i++) {
            CompositeField argType = argsList.get(i);
            CompositeField memType = createCompositeElementsDetails(file, true, argType.getFieldName(),
                    argType.getTypeReference(), true, true, argType.getFieldName() + ": " + argType.getComment());
            file.addClassVariable(false, false, "private", memType, false, null);
        }

        // Tạo constructor rỗng (Mặc định)
        file.addConstructorDefault(returnTypeInfo.getShortName());

        // Tạo constructor có chứa tất cả các tham số
        MethodWriter method = file.addConstructor("public", returnTypeInfo.getShortName(), argsList, null, null,
                "Constructs an instance of this type using provided values.", null);

        for (int i = 0; i < argsList.size(); i++) {
            CompositeField argType = argsList.get(i);
            // Trong C++, để tránh nhầm lẫn giữa parameter và member variable, ta dùng this->
            method.addLine("this->" + argType.getFieldName() + " = " + argType.getFieldName() + ";");
        }
        method.addMethodCloseStatement();

        // Thêm Getters và Setters
        for (int i = 0; i < argsList.size(); i++) {
            CompositeField argType = createCompositeElementsDetails(file, true, argsList.get(i).getFieldName(),
                    returnTypeInfo.getReturnTypes().get(i).getSourceType(), true, true, "The new value.");
            addGetter(file, argType, null);
            addSetter(file, argType, null);
        }

        // TBD: Cần sinh hàm Encode / Decode cho class này giống như CppCompositeClass
        // vì nó kế thừa từ mo::mal::Element
        // ======================= SỬA ĐOẠN ENCODE / DECODE NÀY =======================
        MethodWriter encodeMethod = encodeMethodOpen(file);
        for (int i = 0; i < argsList.size(); i++) {
            CompositeField element = argsList.get(i);
            String call = element.getEncodeCall() != null ? element.getEncodeCall() : "Element";
            encodeMethod.addLine("encoder->encodeNullable" + call + "(this->" + element.getFieldName() + ");");
        }
        encodeMethod.addMethodCloseStatement();

        CompositeField elemType = createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(StdStrings.MAL, null, StdStrings.ELEMENT, false), true, true, null);
        MethodWriter decodeMethod = decodeMethodOpen(file, elemType);
        for (int i = 0; i < argsList.size(); i++) {
            CompositeField element = argsList.get(i);
            String cppType = element.getTypeName();
            String call = element.getDecodeCall() != null ? element.getDecodeCall() : "Element";
            boolean isNative = isNativeType(cppType) && !getNativeType(cppType).isObject();

            if (isNative) {
                decodeMethod.addLine("this->" + element.getFieldName() + " = decoder->decodeNullable" + call + "();");
            } else {
                String castType = cppType.replace("std::shared_ptr<", "").replace(">", "");
                decodeMethod.addLine("this->" + element.getFieldName() + " = std::dynamic_pointer_cast<" + castType + ">(decoder->decodeNullable" + call + "());");
            }
        }
        decodeMethod.addLine("return std::make_shared<" + returnTypeInfo.getShortName() + ">(*this);");
        decodeMethod.addMethodCloseStatement();
        // ============================================================================

        file.addClassCloseStatement();
        file.flush();
    }

    // (Lưu ý: Bạn có thể thêm hàm trống này vào nếu hệ thống gọi nó)
    protected void createServiceMessageBodyFolderComment(String baseFolder, String area, String service) throws IOException {
        // Tùy chọn: Sinh file comment giải thích package (giống package-info.java)
    }
}