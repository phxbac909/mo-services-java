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
package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.ServiceSummary;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;
import esa.mo.xsd.AreaType;
import esa.mo.xsd.ServiceType;

import java.io.File;
import java.io.IOException;

public class CppServiceInfo {

    private final CppGeneratorLangs generator;
    public final static String SERVICE_INFO = "ServiceInfo";

    public CppServiceInfo(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createServiceInfoClass(File serviceFolder, AreaType area, ServiceType service, ServiceSummary summary) throws IOException {
        String serviceName = service.getName();
        String className = serviceName + SERVICE_INFO;
        String serviceCaps = serviceName.toUpperCase();
        String helperClassName = serviceName + "Helper";

        ClassWriter file = generator.createClassFile(serviceFolder, className);
        file.addPackageStatement(area.getName(), serviceName, null);

        // Kế thừa mo::mal::ServiceInfo
        file.addClassOpenStatement(className, false, false, "mo::mal::ServiceInfo", null, "ServiceInfo class for " + serviceName + ".");

        // Constructor
        MethodWriter constructor = file.addConstructor("public", className, null, null, null, "Constructor", null);
        
        // Gọi lớp cha ServiceInfo(...) (Tham chiếu Magenta Book)
        constructor.addLine("super(mo::mal::ServiceKey(" + 
                area.getNumber() + ", " + 
                area.getVersion() + ", " + 
                helperClassName + "::" + serviceCaps + "_SERVICE_NUMBER),");
        constructor.addLine("      " + helperClassName + "::" + serviceCaps + "_SERVICE_NAME,");
        constructor.addLine("      " + helperClassName + "::" + serviceCaps + "_SERVICE_ELEMENTS,");
        constructor.addLine("      " + helperClassName + "::OPERATIONS);");
        constructor.addMethodCloseStatement();

        // Get Area Method
        CompositeField retType = generator.createCompositeElementsDetails(file, false, "return",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::MALArea>", false), false, true, null);
        MethodWriter method = file.addMethodOpenStatementOverride(retType, "getArea", null, null);
        method.addLine("return " + area.getName() + "Helper::" + area.getName().toUpperCase() + "_AREA;");
        method.addMethodCloseStatement();

        file.addClassCloseStatement();
        file.flush();
    }
}