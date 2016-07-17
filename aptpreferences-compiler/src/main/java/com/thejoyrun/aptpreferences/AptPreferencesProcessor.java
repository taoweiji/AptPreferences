package com.thejoyrun.aptpreferences;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

@AutoService(Processor.class)
public class AptPreferencesProcessor extends AbstractProcessor {

    private Elements elementUtils;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(AptPreferences.class.getCanonicalName());
    }

    private void addAptPreferencesManager() {
        System.out.println("AptPreferences:addAptPreferencesManager");
        MethodSpec initMethodSpec = MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(ClassName.get("android.content", "Context"), "context")
                .addParameter(ClassName.get("com.thejoyrun.aptpreferences", "AptParser"), "aptParser")
                .addStatement("sContext = context")
                .addStatement("sAptParser = aptParser")
                .build();
        MethodSpec getContextMethodSpec = MethodSpec.methodBuilder("getContext")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get("android.content", "Context"))
                .addStatement("return sContext")
                .build();
        MethodSpec getAptParserMethodSpec = MethodSpec.methodBuilder("getAptParser")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get("com.thejoyrun.aptpreferences", "AptParser"))
                .addStatement("return sAptParser")
                .build();


        TypeSpec aptPreferencesManager = TypeSpec.classBuilder("AptPreferencesManager")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(initMethodSpec)
                .addMethod(getContextMethodSpec)
                .addMethod(getAptParserMethodSpec)
                .addField(ClassName.get("android.content", "Context"), "sContext", Modifier.PRIVATE, Modifier.STATIC)
                .addField(ClassName.get("com.thejoyrun.aptpreferences", "AptParser"), "sAptParser", Modifier.PRIVATE, Modifier.STATIC)
                .build();
        JavaFile javaFile = JavaFile.builder("com.thejoyrun.aptpreferences", aptPreferencesManager).build();

        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        addAptPreferencesManager();
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(AptPreferences.class);
        System.out.println("!!!!!生成AptPreferences");

        for (Element element : elements) {
            // 判断是否Class
            if (!(element instanceof TypeElement)) {
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            // 获取该类的全部成员，包括
            List<? extends Element> members = elementUtils.getAllMembers(typeElement);
            List<MethodSpec> methodSpecs = new ArrayList<>();
            Set<Element> inClassElements = new HashSet<>();
            for (Element item : members) {
                // 忽略除了成员方法外的元素
                if (!(item instanceof ExecutableElement)) {
                    continue;
                }
                //忽略final、static 方法
                if (item.getModifiers().contains(Modifier.FINAL) || item.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }

                ExecutableElement executableElement = (ExecutableElement) item;
                String name = item.getSimpleName().toString();
                // 忽略基类的一个get方法
                if (name.equals("getClass")) {
                    continue;
                }

                // 忽略不是get、set、is 开头的方法
                boolean getter = false;
                if (name.startsWith("get") || name.startsWith("is")) {
                    getter = true;
                } else if (name.startsWith("set")) {
                    getter = false;
                } else {
                    continue;
                }

                System.out.println(name);
                // 从方法名称提取成员变量的名称
                String fieldName = name.replaceFirst("get|is|set", "");
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                // 根据名称提取成员变量的元素
                Element fieldElement = getElement(members, fieldName);
                if (fieldElement == null) {
                    continue;
                }
                // 检查是否有注解
                AptField annotation = fieldElement.getAnnotation(AptField.class);
                // 检查是否需要保存
                if (annotation != null && !annotation.save()) {
                    continue;
                }
                String modName;
                boolean isDouble = false;
                boolean isObject = false;
                TypeName type = TypeName.get(fieldElement.asType());
                if (type.equals(TypeName.BOOLEAN)) {
                    if (getter) {
                        modName = "getBoolean";
                    } else {
                        modName = "putBoolean";
                    }
                } else if (type.equals(TypeName.INT)) {
                    if (getter) {
                        modName = "getInt";
                    } else {
                        modName = "putInt";
                    }
                } else if (type.equals(TypeName.DOUBLE)) {
                    if (getter) {
                        modName = "getFloat";
                    } else {
                        modName = "putFloat";
                    }
                    isDouble = true;
                } else if (type.equals(TypeName.FLOAT)) {
                    if (getter) {
                        modName = "getFloat";
                    } else {
                        modName = "putFloat";
                    }
                } else if (type.equals(TypeName.LONG)) {
                    if (getter) {
                        modName = "getLong";
                    } else {
                        modName = "putLong";
                    }
                } else if (type.equals(TypeName.get(String.class))) {
                    if (getter) {
                        modName = "getString";
                    } else {
                        modName = "putString";
                    }
                } else {
                    if (getter) {
                        modName = "getString";
                    } else {
                        modName = "putString";
                    }
                    // 检查preferences是否登录true，如果是true代表这个对象作为preferences来保存，否则以对象持久化
                    if (annotation != null && annotation.preferences()) {
                        inClassElements.add(fieldElement);
                        continue;
                    }
                    isObject = true;
                }


                if (name.startsWith("set")) {
                    if (isObject) {
                        MethodSpec setMethod = MethodSpec.overriding(executableElement)
                                .addStatement(String.format("mEdit.putString(\"%1$s\", AptPreferencesManager.getAptParser().serialize(%1$s)).apply();", fieldName))
                                .build();
                        methodSpecs.add(setMethod);
                        continue;
                    }

                    MethodSpec setMethod;
                    if (annotation != null && annotation.commit()) {
                        if (isDouble) {
                            setMethod = MethodSpec.overriding(executableElement)
                                    .addStatement(String.format("mEdit.%s(\"%s\", (float)%s).commit()", modName, fieldName, fieldName)).build();
                        } else {
                            setMethod = MethodSpec.overriding(executableElement)
                                    .addStatement(String.format("mEdit.%s(\"%s\", %s).commit()", modName, fieldName, fieldName)).build();
                        }
                    } else {
                        if (isDouble) {
                            setMethod = MethodSpec.overriding(executableElement)
                                    .addStatement(String.format("mEdit.%s(\"%s\", (float)%s).apply()", modName, fieldName, fieldName)).build();
                        } else {
                            setMethod = MethodSpec.overriding(executableElement)
                                    .addStatement(String.format("mEdit.%s(\"%s\", %s).apply()", modName, fieldName, fieldName)).build();
                        }
                    }
                    methodSpecs.add(setMethod);
                } else {


                    if (isObject) {
                        MethodSpec setMethod = MethodSpec.overriding(executableElement)
                                .addStatement(String.format("String text = mPreferences.getString(\"%s\", null)", fieldName))
                                .addStatement("Object object = null")
                                .addStatement(String.format("if (text != null){\n" +
                                        "            object = AptPreferencesManager.getAptParser().deserialize(%1$s.class,text);\n" +
                                        "        }\n" +
                                        "        if (object != null){\n" +
                                        "            return (%1$s) object;\n" +
                                        "        }", fieldElement.asType().toString()))
                                .addStatement(String.format("return super.%s()", executableElement.getSimpleName()))
                                .build();
                        methodSpecs.add(setMethod);


                        continue;
                    }


                    if (isDouble) {
                        MethodSpec setMethod = MethodSpec.overriding(executableElement)
                                .addStatement(String.format("return mPreferences.%s(\"%s\", (float)super.%s())", modName, fieldName, name))
                                .build();

                        methodSpecs.add(setMethod);
                    } else {
                        MethodSpec setMethod = MethodSpec.overriding(executableElement)
                                .addStatement(String.format("return mPreferences.%s(\"%s\", super.%s())", modName, fieldName, name))
                                .build();

                        methodSpecs.add(setMethod);
                    }
                }
            }

            TypeName targetClassName = ClassName.get(getPackageName(typeElement), element.getSimpleName() + "Preferences");
//
//            System.out.println(getPackageName(typeElement));
//            if (element.getKind() != ElementKind.CLASS) {
//                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "only support class");
//            }
            String getMethodString = String.format("if (sMap.containsKey(name)) {\n" +
                    "            return sMap.get(name);\n" +
                    "        }\n" +
                    "        synchronized (sMap) {\n" +
                    "            if (!sMap.containsKey(name)) {\n" +
                    "                %1$s preferences = new %1$s(name);\n" +
                    "                sMap.put(name, preferences);\n" +
                    "            }\n" +
                    "        }\n" +
                    "        return sMap.get(name)", element.getSimpleName() + "Preferences");


            MethodSpec getMethodSpec = MethodSpec.methodBuilder("get")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(targetClassName)
                    .addParameter(String.class, "name")
                    .addStatement(getMethodString)
                    .build();
            MethodSpec getMethodSpec2 = MethodSpec.methodBuilder("get")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(targetClassName)
                    .addStatement("return get(\"\")")
                    .build();
            MethodSpec clearAllMethodSpec = MethodSpec.methodBuilder("clearAll")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(TypeName.VOID)
                    .addStatement("java.util.Set<String> keys = sMap.keySet();\n" +
                            "        for (String key : keys){\n" +
                            "            sMap.get(key).clear();\n" +
                            "        }")
                    .build();

            MethodSpec clearMethodSpec = MethodSpec.methodBuilder("clear")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.VOID)
                    .addStatement("mEdit.clear().commit()")
                    .addStatement("sMap.remove(mName)")
                    .build();
            System.out.println("建立内部类," + inClassElements.size());

            List<TypeSpec> typeSpecs = getInClassTypeSpec(inClassElements);
            StringBuilder inClassInitString = new StringBuilder();
            for (TypeSpec typeSpec : typeSpecs) {
                System.out.println("##########" + typeSpec.name);
                inClassInitString.append(String.format("this.set%s(new %s());", typeSpec.name.replace("Preferences", ""), typeSpec.name)).append('\n');
            }
            MethodSpec constructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(String.class, "name")
                    .addStatement(String.format("mPreferences = AptPreferencesManager.getContext().getSharedPreferences(\"%s_\" + name, 0)", element.getSimpleName()))
                    .addStatement("mEdit = mPreferences.edit()")
                    .addStatement("this.mName = name")
                    .addStatement(inClassInitString.toString())
                    .build();

            FieldSpec fieldSpec = FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), targetClassName), "sMap", Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                    .initializer("new java.util.HashMap<>()")
                    .build();
            TypeSpec typeSpec = TypeSpec.classBuilder(element.getSimpleName() + "Preferences")
                    .superclass(TypeName.get(typeElement.asType()))
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addMethods(methodSpecs)
                    .addMethod(getMethodSpec)
                    .addMethod(getMethodSpec2)
                    .addMethod(constructor)
                    .addMethod(clearMethodSpec)
                    .addMethod(clearAllMethodSpec)
                    .addField(ClassName.get("android.content", "SharedPreferences", "Editor"), "mEdit",Modifier.PRIVATE,Modifier.FINAL)
                    .addField(ClassName.get("android.content", "SharedPreferences"), "mPreferences",Modifier.PRIVATE,Modifier.FINAL)
                    .addField(String.class, "mName",Modifier.PRIVATE,Modifier.FINAL)
                    .addField(fieldSpec)
                    .addTypes(typeSpecs)
                    .build();
            JavaFile javaFile = JavaFile.builder(getPackageName(typeElement), typeSpec).build();

            try {
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return true;
    }

    private List<TypeSpec> getInClassTypeSpec(Set<Element> inClassElements) {
        List<TypeSpec> typeSpecs = new ArrayList<>();
        for (Element element : inClassElements) {
            TypeElement typeElement = elementUtils.getTypeElement(TypeName.get(element.asType()).toString());

            List<? extends Element> members = elementUtils.getAllMembers(typeElement);
            List<MethodSpec> methodSpecs = new ArrayList<>();
            for (Element item : members) {

                if (item instanceof ExecutableElement) {
                    ExecutableElement executableElement = (ExecutableElement) item;
                    String name = item.getSimpleName().toString();
                    if (name.equals("getClass")) {
                        continue;
                    }
                    System.out.println(name);
                    //忽略final、static 方法
                    if (executableElement.getModifiers().contains(Modifier.FINAL) || executableElement.getModifiers().contains(Modifier.STATIC)) {
                        continue;
                    }
                    if (!name.startsWith("get") && !name.startsWith("is") && !name.startsWith("set")) {
                        continue;
                    }
                    String fieldName = name.replaceFirst("get|is|set", "");
                    fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);

                    Element fieldElement = getElement(members, fieldName);
                    AptField annotation = item.getAnnotation(AptField.class);
                    if (annotation != null && !annotation.save()) {
                        continue;
                    }
                    System.out.println(name);
                    TypeName type = TypeName.get(fieldElement.asType());


                    if (name.startsWith("set")) {
                        System.out.println("设置：" + name);
                        String modName;
                        boolean isDouble = false;
                        if (type.equals(TypeName.BOOLEAN)) {
                            modName = "putBoolean";
                        } else if (type.equals(TypeName.INT)) {
                            modName = "putInt";
                        } else if (type.equals(TypeName.DOUBLE)) {
                            modName = "putFloat";
                            isDouble = true;
                        } else if (type.equals(TypeName.FLOAT)) {
                            modName = "putFloat";
                        } else if (type.equals(TypeName.LONG)) {
                            modName = "putLong";
                        } else {
                            modName = "putString";
                        }
                        MethodSpec setMethod;

                        if (annotation != null && annotation.commit()) {
                            if (isDouble) {
                                setMethod = MethodSpec.overriding(executableElement)
                                        .addStatement(String.format("mEdit.%s(\"%s\", (float)%s).commit()", modName, typeElement.getSimpleName() + "." + fieldName, fieldName)).build();
                            } else {
                                setMethod = MethodSpec.overriding(executableElement)
                                        .addStatement(String.format("mEdit.%s(\"%s\", %s).commit()", modName, typeElement.getSimpleName() + "." + fieldName, fieldName)).build();
                            }
                        } else {
                            if (isDouble) {
                                setMethod = MethodSpec.overriding(executableElement)
                                        .addStatement(String.format("mEdit.%s(\"%s\", (float)%s).apply()", modName, typeElement.getSimpleName() + "." + fieldName, fieldName)).build();
                            } else {
                                setMethod = MethodSpec.overriding(executableElement)
                                        .addStatement(String.format("mEdit.%s(\"%s\", %s).apply()", modName, typeElement.getSimpleName() + "." + fieldName, fieldName)).build();
                            }
                        }


                        methodSpecs.add(setMethod);
                    } else if (name.startsWith("get") || name.startsWith("is")) {
                        System.out.println("获取：" + name);
//                        TypeName type = TypeName.get(executableElement.getReturnType());
                        String modName;
                        boolean isDouble = false;
                        if (type.equals(TypeName.BOOLEAN)) {
                            modName = "getBoolean";
                        } else if (type.equals(TypeName.INT)) {
                            modName = "getInt";
                        } else if (type.equals(TypeName.DOUBLE)) {
                            modName = "getFloat";
                            isDouble = true;
                        } else if (type.equals(TypeName.FLOAT)) {
                            modName = "getFloat";
                        } else if (type.equals(TypeName.LONG)) {
                            modName = "getLong";
                        } else {
                            modName = "getString";
                        }


                        if (isDouble) {
                            MethodSpec setMethod = MethodSpec.overriding(executableElement)
                                    .addStatement(String.format("return mPreferences.%s(\"%s\", (float)super.%s())", modName, typeElement.getSimpleName() + "." + fieldName, name))
                                    .build();

                            methodSpecs.add(setMethod);
                        } else {
                            MethodSpec setMethod = MethodSpec.overriding(executableElement)
                                    .addStatement(String.format("return mPreferences.%s(\"%s\", super.%s())", modName, typeElement.getSimpleName() + "." + fieldName, name))
                                    .build();

                            methodSpecs.add(setMethod);
                        }

                    }

                }
            }
            TypeSpec typeSpec = TypeSpec.classBuilder(typeElement.getSimpleName() + "Preferences")
                    .superclass(TypeName.get(element.asType()))
                    .addModifiers(Modifier.PRIVATE)
                    .addMethods(methodSpecs)
                    .build();
            typeSpecs.add(typeSpec);
        }
        return typeSpecs;
    }


    private <T extends Annotation> T getAnnotation(List<? extends Element> members, String fieldName, Class<T> aptFieldClass) {
        for (Element item : members) {
            if (item.getSimpleName().toString().equals(fieldName)) {
                Annotation annotation = item.getAnnotation(aptFieldClass);
                return (T) annotation;
            }
        }
        return null;
    }

    private Element getElement(List<? extends Element> members, String fieldName) {
        for (Element item : members) {
            if (item.getSimpleName().toString().equals(fieldName)) {
                return item;
            }
        }
        return null;
    }


    private String getPackageName(TypeElement type) {
        return elementUtils.getPackageOf(type).getQualifiedName().toString();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
    }

    /**
     * If the processor class is annotated with {@link
     * }, return the source version in the
     * annotation.  If the class is not so annotated, {@link
     * SourceVersion#RELEASE_6} is returned.
     *
     * @return the latest source version supported by this processor
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }
}