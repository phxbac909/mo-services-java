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

import esa.mo.tools.stubgen.GeneratorCpp;
import esa.mo.tools.stubgen.StubUtils;
import esa.mo.tools.stubgen.specification.CompositeField;
import esa.mo.tools.stubgen.specification.NativeTypeDetails;
import esa.mo.tools.stubgen.specification.StdStrings;
import esa.mo.tools.stubgen.writers.AbstractLanguageWriter;
import esa.mo.tools.stubgen.writers.ClassWriter;
import esa.mo.tools.stubgen.writers.InterfaceWriter;
import esa.mo.tools.stubgen.writers.MethodWriter;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

/**
 * C++ specific writer.
 * Handles the generation of BOTH .hpp (Header) and .cpp (Source) files simultaneously.
 * Adheres strictly to C++11 and CCSDS 523.2-M-1 Magenta Book standards.
 */
public class CppClassWriter extends AbstractLanguageWriter implements ClassWriter, InterfaceWriter, MethodWriter {

    private final Writer hppFile;
    private final Writer cppFile;
    private final GeneratorCpp generator;

    private final String className;
    private String baseClassName; // Dùng để xử lý từ khóa 'super' trong C++
    private int namespaceCount = 0; // Đếm số lượng namespace để đóng '}' cho đúng

    public CppClassWriter(File folder, String className, GeneratorCpp generator) throws IOException {
        this.className = className;
        this.generator = generator;
        this.hppFile = StubUtils.createLowLevelWriter(folder, className, GeneratorCpp.CPP_HEADER_EXT);
        this.cppFile = StubUtils.createLowLevelWriter(folder, className, GeneratorCpp.CPP_SOURCE_EXT);
        
        writeHeaderGuardsAndIncludes();
    }

    public CppClassWriter(String destinationFolderName, String className, GeneratorCpp generator) throws IOException {
        this.className = className;
        this.generator = generator;
        this.hppFile = StubUtils.createLowLevelWriter(destinationFolderName, className, GeneratorCpp.CPP_HEADER_EXT);
        this.cppFile = StubUtils.createLowLevelWriter(destinationFolderName, className, GeneratorCpp.CPP_SOURCE_EXT);
        
        writeHeaderGuardsAndIncludes();
    }

    private void writeHeaderGuardsAndIncludes() throws IOException {
        String guard = "_" + className.toUpperCase() + "_HPP_";
        hppFile.append("#ifndef ").append(guard).append("\n");
        hppFile.append("#define ").append(guard).append("\n\n");

        // Các thư viện C++ cơ bản thường dùng theo chuẩn Magenta Book
        hppFile.append("#include <memory>\n");
        hppFile.append("#include <string>\n");
        hppFile.append("#include <vector>\n");
        hppFile.append("#include <cstdint>\n");
        hppFile.append("#include <stdexcept>\n\n");

        cppFile.append("#include \"").append(className).append(".hpp\"\n\n");
    }

    @Override
    public void addPackageStatement(String area, String service, String extraPackage) throws IOException {
        List<String> namespaces = new LinkedList<>();
        namespaces.add("mo");
        namespaces.add("mal");

        if (area != null && !area.isEmpty()) namespaces.add(area.toLowerCase());
        if (service != null && !service.isEmpty()) namespaces.add(service.toLowerCase());
        if (extraPackage != null && !extraPackage.isEmpty()) namespaces.add(extraPackage.toLowerCase());

        StringBuilder nsBuilder = new StringBuilder();
        for (String ns : namespaces) {
            nsBuilder.append("namespace ").append(ns).append(" {\n");
            namespaceCount++;
        }
        
        // Ghi khai báo namespace vào cả 2 file
        hppFile.append(nsBuilder.toString()).append("\n");
        cppFile.append(nsBuilder.toString()).append("\n");
    }

    @Override
    public void addMultilineComment(int tabCount, boolean preBlankLine, List<String> comments, boolean postBlankLine) throws IOException {

    }

    // =========================================================================
    // XỬ LÝ CLASS / INTERFACE OPEN & CLOSE
    // =========================================================================

    @Override
    public void addClassOpenStatement(String className, boolean finalClass, boolean abstractClass, String extendsClass, String implementsInterface, String comment) throws IOException {
        addMultilineCommentToWriter(hppFile, 0, true, comment, false);

        this.baseClassName = extendsClass;

        StringBuilder signature = new StringBuilder("class ").append(className);
        if (finalClass) {
            signature.append(" final");
        }

        boolean hasBase = false;
        if (extendsClass != null && !extendsClass.isEmpty()) {
            signature.append(" : public ").append(formatTypeNamespace(extendsClass));
            hasBase = true;
        }

        if (implementsInterface != null && !implementsInterface.isEmpty()) {
            String[] interfaces = implementsInterface.split(",");
            for (String iface : interfaces) {
                signature.append(hasBase ? ", public " : " : public ").append(formatTypeNamespace(iface.trim()));
                hasBase = true;
            }
        }

        signature.append(" {\npublic:\n");
        hppFile.append(signature.toString());
    }

    @Override
    public void addInterfaceOpenStatement(String interfaceName, String extendsInterface, String comment) throws IOException {
        addClassOpenStatement(interfaceName, false, true, extendsInterface, null, comment);
    }

    @Override
    public void addClassCloseStatement() throws IOException {
        hppFile.append("};\n\n");
        closeNamespaces();
    }

    @Override
    public void addInterfaceCloseStatement() throws IOException {
        addClassCloseStatement();
    }

    private void closeNamespaces() throws IOException {
        StringBuilder closing = new StringBuilder();
        for (int i = 0; i < namespaceCount; i++) {
            closing.append("} ");
        }
        closing.append("\n");

        hppFile.append(closing.toString());
        cppFile.append(closing.toString());

        // Đóng Include guard cho file header
        hppFile.append("\n#endif // _").append(className.toUpperCase()).append("_HPP_\n");
    }

    // =========================================================================
    // XỬ LÝ BIẾN (VARIABLES)
    // =========================================================================

    @Override
    public void addClassVariable(boolean isStatic, boolean isFinal, String scope, CompositeField field, boolean isObject, String initialValue) throws IOException {
        addClassVariable(false, isStatic, isFinal, scope, field, isObject, false, initialValue);
    }

    @Override
    public void addClassVariable(boolean isStatic, boolean isFinal, String scope, CompositeField field, boolean isObject, boolean isArray, List<String> initialValues) throws IOException {
        String val = initialValues != null && !initialValues.isEmpty() ? "{" + String.join(", ", initialValues) + "}" : null;
        addClassVariable(false, isStatic, isFinal, scope, field, isObject, isArray, val);
    }

    @Override
    public void addClassVariableNewInit(boolean isStatic, boolean isFinal, String scope, CompositeField arg, boolean isObject, boolean isArray, String initialValue, boolean isNewInit) throws IOException {
        addClassVariable(false, isStatic, isFinal, scope, arg, isObject, isArray, initialValue);
    }

    @Override
    public void addClassVariableDeprecated(boolean isStatic, boolean isFinal, String scope, CompositeField field, boolean isObject, String initialValue) throws IOException {
        addClassVariable(true, isStatic, isFinal, scope, field, isObject, false, initialValue);
    }

    private void addClassVariable(boolean isDeprecated, boolean isStatic, boolean isFinal, String scope, CompositeField field, boolean isObject, boolean isArray, String initialValue) throws IOException {
        addMultilineCommentToWriter(hppFile, 1, false, field.getComment(), false);

        StringBuilder decl = new StringBuilder();
        // C++ scope (public/private) thường được nhóm lại thành các block (public:, private:)
        // Ở đây để đơn giản ta tạm thời bỏ qua scope keyword cho mỗi dòng biến hoặc có thể ghi kèm
        if (isStatic) decl.append("static ");
        if (isFinal) decl.append("const ");

        String type = createLocalType(field, false, false);
        if (isArray) type = "std::vector<" + type + ">";
        
        decl.append(type).append(" ").append(field.getFieldName());

        // Trong C++11 có thể gán giá trị trực tiếp trên header
        if (initialValue != null && !initialValue.isEmpty()) {
            if (initialValue.startsWith("new ")) {
                // Đổi 'new X()' thành 'std::make_shared<X>()'
                String innerType = initialValue.replace("new ", "").replace("()", "");
                decl.append(" = std::make_shared<").append(innerType).append(">()");
            } else {
                decl.append(" = ").append(initialValue);
            }
        }
        decl.append(";\n");

        hppFile.append(makeLine(1, decl.toString()));
    }

    // =========================================================================
    // XỬ LÝ HÀM VÀ CONSTRUCTOR
    // =========================================================================

    @Override
    public void addConstructorDefault(String className) throws IOException {
        addConstructor("public", className, (CompositeField) null, false, null, "Default constructor", null).addMethodCloseStatement();
    }

    @Override
    public void addConstructorCopy(String className, List<CompositeField> compElements) throws IOException {
        // C++ tự động sinh default copy constructor, nhưng nếu cần:
        hppFile.append(makeLine(1, className + "(const " + className + "& other) = default;\n"));
    }

    @Override
    public MethodWriter addConstructor(String scope, String className, CompositeField arg, boolean isArgForSuper, String throwsSpec, String comment, String throwsComment) throws IOException {
        List<CompositeField> args = arg != null ? java.util.Arrays.asList(arg) : new LinkedList<>();
        List<CompositeField> superArgs = isArgForSuper ? args : new LinkedList<>();
        return addConstructor(scope, className, args, superArgs, throwsSpec, comment, throwsComment);
    }

    @Override
    public MethodWriter addConstructor(String scope, String className, List<CompositeField> args, List<CompositeField> superArgs, String throwsSpec, String comment, String throwsComment) throws IOException {
        addMultilineCommentToWriter(hppFile, 1, false, comment, false);

        String argString = processArgs(args);
        
        // HPP: Khai báo constructor
        hppFile.append(makeLine(1, className + "(" + argString + ");\n"));

        // CPP: Định nghĩa constructor kèm theo Initializer List cho lớp cha (nếu có)
        StringBuilder cppSig = new StringBuilder(className).append("::").append(className).append("(").append(argString).append(")");
        
        if (superArgs != null && !superArgs.isEmpty() && baseClassName != null) {
            cppSig.append(" : ").append(formatTypeNamespace(baseClassName)).append("(");
            boolean first = true;
            for (CompositeField sa : superArgs) {
                if (!first) cppSig.append(", ");
                cppSig.append(sa.getFieldName());
                first = false;
            }
            cppSig.append(")");
        }
        cppSig.append(" {\n");
        cppFile.append(cppSig.toString());

        return this;
    }

    @Override
    public MethodWriter addMethodOpenStatement(boolean isConst, boolean isStatic, String scope, boolean isReturnConst, boolean isReturnActual, CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec) throws IOException {
        return addMethodOpenStatement(false, isConst, isStatic, scope, isReturnConst, isReturnActual, rtype, methodName, args, throwsSpec, null, null, null);
    }

    @Override
    public MethodWriter addMethodOpenStatement(boolean isConst, boolean isStatic, String scope, boolean isReturnConst, boolean isReturnActual, CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec, String comment, String returnComment, List<String> throwsComment) throws IOException {
        return addMethodOpenStatement(false, isConst, isStatic, scope, isReturnConst, isReturnActual, rtype, methodName, args, throwsSpec, comment, returnComment, throwsComment);
    }

    @Override
    public MethodWriter addMethodOpenStatementOverride(CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec) throws IOException {
        return addMethodOpenStatement(false, true, false, false, "public", false, false, rtype, methodName, args, throwsSpec, "Overrides " + methodName, null, null, false);
    }

    @Override
    public MethodWriter addMethodOpenStatement(boolean isVirtual, boolean isConst, boolean isStatic, String scope, boolean isReturnConst, boolean isReturnActual, CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec) throws IOException {
        return addMethodOpenStatement(isVirtual, isConst, isStatic, scope, isReturnConst, isReturnActual, rtype, methodName, args, throwsSpec, null, null, null);
    }

    @Override
    public MethodWriter addMethodOpenStatement(boolean isVirtual, boolean isConst, boolean isStatic, String scope, boolean isReturnConst, boolean isReturnActual, CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec, String comment, String returnComment, List<String> throwsComment) throws IOException {
        return addMethodOpenStatement(false, isVirtual, isConst, isStatic, scope, isReturnConst, isReturnActual, rtype, methodName, args, throwsSpec, comment, returnComment, throwsComment, false);
    }

    @Override
    public MethodWriter addMethodOpenStatement(boolean isFinal, boolean isVirtual, boolean isConst, boolean isStatic, String scope, boolean isReturnConst, boolean isReturnActual, CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec, String comment, String returnComment, List<String> throwsComment) throws IOException {
        return addMethodOpenStatement(isFinal, isVirtual, isConst, isStatic, scope, isReturnConst, isReturnActual, rtype, methodName, args, throwsSpec, comment, returnComment, throwsComment, false);
    }

    @Override
    public MethodWriter addMethodOpenStatement(boolean isFinal, boolean isVirtual, boolean isConst, boolean isStatic, String scope, boolean isReturnConst, boolean isReturnActual, CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec, String comment, String returnComment, List<String> throwsComment, boolean isDeprecated) throws IOException {
        addMultilineCommentToWriter(hppFile, 1, false, comment, false);

        String returnTypeStr = createLocalType(rtype, false, true);
        String argString = processArgs(args);

        // ==========================================
        // 1. HEADER FILE (.hpp)
        // ==========================================
        StringBuilder hppSig = new StringBuilder();
        if (isStatic) hppSig.append("static ");
        if (isVirtual) hppSig.append("virtual ");
        
        hppSig.append(returnTypeStr).append(" ").append(methodName).append("(").append(argString).append(")");
        
        if (isConst) hppSig.append(" const");
        if (isFinal || isVirtual) hppSig.append(" override"); // Đơn giản hóa: virtual thường đi đôi với override trong generator này
        
        hppFile.append(makeLine(1, hppSig.toString() + ";\n"));

        // ==========================================
        // 2. SOURCE FILE (.cpp)
        // ==========================================
        StringBuilder cppSig = new StringBuilder();
        cppSig.append(returnTypeStr).append(" ").append(className).append("::").append(methodName).append("(").append(argString).append(")");
        if (isConst) cppSig.append(" const");
        cppSig.append(" {\n");

        cppFile.append(cppSig.toString());

        return this; // Trả về this vì class này chính là MethodWriter
    }

    // =========================================================================
    // METHOD WRITER IMPLEMENTATION (Ghi vào .cpp)
    // =========================================================================

    @Override
    public void addSuperMethodStatement(String method, String args) throws IOException {
        // Trong C++ không có từ khóa super, phải gọi BaseClass::method(args)
        if (baseClassName != null && !baseClassName.isEmpty()) {
            cppFile.append(makeLine(1, formatTypeNamespace(baseClassName) + "::" + method + "(" + args + ");"));
        }
    }

    @Override
    public void addLine(String line) throws IOException {
        // Lệnh null/new của Java cần thay thế cho C++
        line = line.replace(" null", " nullptr");
        
        // Đơn giản hóa việc bắt một số cấu trúc new Object() -> std::make_shared<Object>()
        if (line.contains("new ")) {
            line = line.replaceAll("new ([A-Za-z0-9_:]+)\\((.*?)\\)", "std::make_shared<$1>($2)");
        }

        cppFile.append(makeLine(1, line));
    }

    @Override
    public void addArrayMethodStatement(String arrayVariable, String indexVariable, String arrayMaxSize) throws IOException {
        cppFile.append(makeLine(1, "return " + arrayVariable + "[" + indexVariable + "];"));
    }

    @Override
    public void addMethodCloseStatement() throws IOException {
        cppFile.append("}\n\n");
    }

    // =========================================================================
    // TIỆN ÍCH TRỢ GIÚP (HELPER METHODS)
    // =========================================================================

    private String processArgs(List<CompositeField> args) {
        if (args == null || args.isEmpty()) return "";
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (CompositeField arg : args) {
            if (arg == null) continue; // <-- THÊM DÒNG NÀY ĐỂ BẢO VỆ

            if (!first) buf.append(", ");
            buf.append(createLocalType(arg, true, false)).append(" ").append(arg.getFieldName());
            first = false;
        }
        return buf.toString();
    }

    /**
     * Chuyển đổi kiểu dữ liệu từ Core sang C++.
     * Tuân thủ quy định: Primitive (truyền trị), Object/Element (truyền const std::shared_ptr<T>&)
     */
    private String createLocalType(CompositeField type, boolean isArgument, boolean isReturn) {
        if (type == null) return "void";
        
        String typeName = formatTypeNamespace(type.getTypeName());
        
        // Loại bỏ các tàn dư của Java
        typeName = typeName.replace(".ElementList", "::HeterogeneousList");

        if (generator.isNativeType(typeName)) {
            NativeTypeDetails dets = generator.getNativeType(typeName);
            if (!dets.isObject()) {
                // Kiểu nguyên thủy (int32_t, bool...) -> Truyền giá trị
                if (type.isList()) {
                    return isArgument ? "const std::vector<" + typeName + ">&" : "std::vector<" + typeName + ">";
                }
                return typeName;
            }
        }

        // Với các Object, Element của MAL
        if (type.isList()) {
            String vectorType = "std::vector<std::shared_ptr<" + typeName + ">>";
            return isArgument ? "const " + vectorType + "&" : vectorType;
        } else {
            String ptrType = "std::shared_ptr<" + typeName + ">";
            return isArgument ? "const " + ptrType + "&" : ptrType;
        }
    }

    private String formatTypeNamespace(String type) {
        if (type == null) return null;
        return type.replace(".", "::");
    }

    private void addMultilineCommentToWriter(Writer targetFile, int tabCount, boolean preBlankLine, String comment, boolean postBlankLine) throws IOException {
        if (comment == null || comment.isEmpty()) return;
        if (preBlankLine) targetFile.append("\n");
        targetFile.append(makeLine(tabCount, "/**"));
        
        String[] lines = comment.split("\n");
        for (String line : lines) {
            targetFile.append(makeLine(tabCount, " * " + line.trim()));
        }
        
        targetFile.append(makeLine(tabCount, " */"));
        if (postBlankLine) targetFile.append("\n");
    }

    @Override
    public void addInterfaceMethodDeclaration(String scope, CompositeField rtype, String methodName, List<CompositeField> args, String throwsSpec, String comment, String returnComment, List<String> throwsComment) throws IOException {
        // Interface trong C++ là class chứa phương thức ảo thuần túy (pure virtual function = 0)
        addMultilineCommentToWriter(hppFile, 1, false, comment, false);
        String returnTypeStr = createLocalType(rtype, false, true);
        String argString = processArgs(args);
        
        hppFile.append(makeLine(1, "virtual " + returnTypeStr + " " + methodName + "(" + argString + ") = 0;\n"));
    }

//    @Override
//    public void addStatement(String string) throws IOException {
//        cppFile.append(string).append("\n");
//    }

    @Override
    public void addStaticConstructor(String returnType, String methodName, String args, String constructorCall) throws IOException {
        // Khai báo trong HPP
        hppFile.append(makeLine(1, "static " + returnType + " " + methodName + "(" + args + ");\n"));
        // Định nghĩa trong CPP
        cppFile.append(returnType).append(" ").append(className).append("::").append(methodName).append("(").append(args).append(") {\n");
        cppFile.append(makeLine(1, constructorCall));
        cppFile.append("}\n\n");
    }

    @Override
    public void flush() throws IOException {
        hppFile.flush();
        cppFile.flush();
    }

    public void addSourceStatement(String statement) throws IOException {
        cppFile.append(statement).append("\n");
    }

    @Override
    public void addStatement(String string) throws IOException {
        // addStatement của JavaClassWriter mặc định ghi vào class body (tương đương .hpp của C++)
        hppFile.append(makeLine(1, string));
    }
}