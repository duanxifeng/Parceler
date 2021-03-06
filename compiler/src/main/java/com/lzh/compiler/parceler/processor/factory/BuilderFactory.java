package com.lzh.compiler.parceler.processor.factory;

import com.lzh.compiler.parceler.annotation.BundleBuilder;
import com.lzh.compiler.parceler.processor.model.Constants;
import com.lzh.compiler.parceler.processor.model.ElementParser;
import com.lzh.compiler.parceler.processor.model.FieldData;
import com.lzh.compiler.parceler.processor.util.UtilMgr;
import com.lzh.compiler.parceler.processor.util.Utils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * @author haoge on 2017/12/25.
 */
public class BuilderFactory {
    private TypeElement type;
    private List<FieldData> list;

    public BuilderFactory(TypeElement type, ElementParser parser) {
        this.type = type;
        this.list = parser == null ? new ArrayList<FieldData>() : parser.getFieldsList();
    }

    public void generate() throws IOException {
        // check if annotated by BundleBuilder
        if (type.getAnnotation(BundleBuilder.class) == null) {
            return;
        }

        // create package and class name of generating class
        String packName = Utils.getPackageName(type);
        String clzName = type.getQualifiedName().toString();
        clzName = Utils.isEmpty(packName) ? clzName + Constants.BUILDER_SUFFIX
                : clzName.substring(packName.length() + 1).replace(".","$") + Constants.BUILDER_SUFFIX;

        TypeName builderClassName = ClassName.bestGuess(clzName);

        // create BuilderClass builder.
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(clzName)
                .addSuperinterface(Constants.CLASS_IBUNDLEBUILDER)
                .addModifiers(Modifier.PUBLIC);

        // create BundleFactory field
        classBuilder.addField(Constants.CLASS_FACTORY, "factory", Modifier.PRIVATE);

        // create create method
        classBuilder.addMethod(MethodSpec.methodBuilder("create")
                .addParameter(Constants.CLASS_BUNDLE, "bundle")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("$T builder = new $T()", builderClassName, builderClassName)
                .addStatement("builder.factory = $T.createFactory(bundle)", Constants.CLASS_PARCELER)
                .addStatement("return builder")
                .build());

        // create getTarget static method
        classBuilder.addMethod(MethodSpec.methodBuilder("getTarget")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(Class.class)
                .addStatement("return $T.class", type)
                .build());

        // create getFactory method
        classBuilder.addMethod(MethodSpec.methodBuilder("getFactory")
                .returns(Constants.CLASS_FACTORY)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addStatement("return factory")
                .build());

        // create getBundle method
        classBuilder.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(Constants.CLASS_BUNDLE)
                .addStatement("return factory.getBundle()")
                .build());

        // create setter methods
        for (FieldData field : list) {
            MethodSpec.Builder builder = MethodSpec.methodBuilder(Utils.combineMethodName(field.getKey(), "set"))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(TypeName.get(field.getVar().asType()), field.getKey());

            // add javadoc
            builder.addJavadoc("@see $T#$N", type, field.getVar().getSimpleName());

            if (field.getConverter() != null) {
                builder.addStatement("factory.setConverter($T.class)", field.getConverter())
                        .addStatement("factory.put($S, $L)", field.getKey(), field.getKey())
                        .addStatement("factory.setConverter(null)");
            } else {
                builder.addStatement("factory.put($S, $L)", field.getKey(), field.getKey());
            }

            classBuilder.addMethod(builder.addStatement("return this").build());

        }

        JavaFile.Builder builder = JavaFile.builder(packName, classBuilder.build());
        builder.addFileComment("The class is generated by Parceler, do not modify!");
        JavaFile build = builder.build();

        build.writeTo(UtilMgr.getMgr().getFiler());
    }
}
