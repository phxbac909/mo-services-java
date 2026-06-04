package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.GeneratorBase;
import esa.mo.tools.stubgen.specification.AttributeTypeDetails;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.StdStrings;
import esa.mo.xsd.TypeReference;

public class CppCompositeFields {

    private final GeneratorBase generator;

    public CppCompositeFields(GeneratorBase generator) {
        this.generator = generator;
    }

    public CompositeField createCompositeElementsDetails(boolean checkType, String fieldName, 
                                                         TypeReference elementType, boolean isStructure, 
                                                         boolean canBeNull, String comment) {
        String typeName = elementType.getName();
        boolean isObjectRef = GeneratorBase.isObjectRef(elementType);

        if (elementType.isList()) {
            String fqTypeName;
            if (generator.isAttributeNativeType(elementType)) {
                fqTypeName = generator.createElementType(StdStrings.MAL, null, typeName + "List");
            } else {
                if (isObjectRef) {
                    fqTypeName = "mo::mal::structures::ObjectRefList";
                } else {
                    fqTypeName = generator.createElementType(elementType, true) + "List";
                }
            }

            // Trong C++, chúng ta dùng std::make_shared để khởi tạo
            String newCall = "std::make_shared<" + fqTypeName + ">()";
            return new CompositeField(fqTypeName, elementType, fieldName, true, canBeNull, false, 
                    StdStrings.ELEMENT, "(" + fqTypeName + ") ", StdStrings.ELEMENT, true, newCall, comment);

        } else if (generator.isAttributeType(elementType)) {
            AttributeTypeDetails details = generator.getAttributeDetails(elementType);
            String fqTypeName = generator.createElementType(elementType, isStructure);
            // Nếu là Native (vd int32_t, bool), không cần dùng make_shared
            String newCall = details.isNativeType() ? details.getDefaultValue() : "std::make_shared<" + fqTypeName + ">()";
            
            return new CompositeField(details.getTargetType(), elementType, fieldName, false, canBeNull, false, 
                    typeName, "", typeName, false, newCall, comment);
        } else {
            String fqTypeName = generator.createElementType(elementType, isStructure);
            String newCall = "std::make_shared<" + fqTypeName + ">()";

            return new CompositeField(fqTypeName, elementType, fieldName, false, canBeNull, false, 
                    StdStrings.ELEMENT, "(" + fqTypeName + ") ", StdStrings.ELEMENT, true, newCall, comment);
        }
    }
}