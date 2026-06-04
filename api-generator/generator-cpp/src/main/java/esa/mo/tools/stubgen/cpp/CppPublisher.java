package esa.mo.tools.stubgen.cpp;

import esa.mo.tools.stubgen.CppGeneratorLangs;
import esa.mo.tools.stubgen.RequiredPublisher;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.TypeUtils;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;

import java.io.IOException;
import java.util.Arrays;

public class CppPublisher {

    private final CppGeneratorLangs generator;

    public CppPublisher(CppGeneratorLangs generator) {
        this.generator = generator;
    }

    public void createPublisherClass(String destinationFolderName, String fqPublisherName, RequiredPublisher op) throws IOException {
        String publisherName = fqPublisherName.substring(fqPublisherName.lastIndexOf('.') + 1);
        String folderPath = fqPublisherName.substring(0, fqPublisherName.lastIndexOf('.')).replace(".", "/");

        ClassWriter file = generator.createClassFile(destinationFolderName + "/" + folderPath, publisherName);
        file.addPackageStatement(op.getArea(), op.getService(), CppGeneratorLangs.PROVIDER_FOLDER);

        file.addClassOpenStatement(publisherName, false, false, null, null, "Publisher class for " + op.getOperation().getName());

        // Field MALPublisherSet (Mục 4.4.4.1.5)
        CompositeField pubSet = generator.createCompositeElementsDetails(file, false, "publisherSet",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::provider::MALPublisherSet>", false), false, true, null);
        file.addClassVariable(false, false, "private", pubSet, false, null);

        // Constructor (Mục 4.4.4.1.6)
        MethodWriter method = file.addConstructor("public", publisherName, pubSet, false, null, "Constructor", null);
        method.addLine("this->publisherSet = publisherSet;");
        method.addMethodCloseStatement();

        // Register Method
        CompositeField entityKeyList = generator.createCompositeElementsDetails(file, false, "entityKeyList",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::EntityKeyList>", false), false, true, null);
        CompositeField listener = generator.createCompositeElementsDetails(file, false, "listener",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::provider::MALPublishInteractionListener>", false), false, true, null);
        
        MethodWriter regMethod = file.addMethodOpenStatement(false, false, false, "public", false, false, null, "syncRegister", Arrays.asList(entityKeyList, listener), null);
        regMethod.addLine("publisherSet->syncRegister(entityKeyList, listener);");
        regMethod.addMethodCloseStatement();

        // Publish Method
        CompositeField updateHeaderList = generator.createCompositeElementsDetails(file, false, "updateHeaderList",
                TypeUtils.createTypeReference(null, null, "std::shared_ptr<mo::mal::UpdateHeaderList>", false), false, true, null);
        CompositeField updateLists = generator.createCompositeElementsDetails(file, false, "updateLists",
                TypeUtils.createTypeReference(null, null, "std::vector<std::shared_ptr<mo::mal::Element>>", false), false, true, null);
        
        MethodWriter pubMethod = file.addMethodOpenStatement(false, false, false, "public", false, false, null, "publish", Arrays.asList(updateHeaderList, updateLists), null);
        pubMethod.addLine("publisherSet->publish(updateHeaderList, updateLists);");
        pubMethod.addMethodCloseStatement();

        // Deregister Method
        MethodWriter deregMethod = file.addMethodOpenStatement(false, false, false, "public", false, false, null, "deregister", null, null);
        deregMethod.addLine("publisherSet->deregister();");
        deregMethod.addMethodCloseStatement();

        file.addClassCloseStatement();
        file.flush();
    }
}