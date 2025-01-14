package com.ganguo.java.plugin.action.generate;

import com.ganguo.java.plugin.constant.AnnotationNames;
import com.ganguo.java.plugin.constant.Constant;
import com.ganguo.java.plugin.context.JavaFileContext;
import com.ganguo.java.plugin.util.ActionShowHelper;
import com.ganguo.java.plugin.util.IndexUtils;
import com.ganguo.java.plugin.util.PsiUtils;
import com.ganguo.java.plugin.util.WriteActions;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.extern.slf4j.Slf4j;
import org.dependcode.dependcode.ContextBuilder;
import org.dependcode.dependcode.anno.Func;
import org.dependcode.dependcode.anno.ImportFrom;
import org.dependcode.dependcode.anno.Var;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapping自动添加ignore
 */
@Slf4j
@ImportFrom(JavaFileContext.class)
public class AddMappingIgnoreAction extends BaseGenerateAction {

    @Override
    protected void action(AnActionEvent e) throws Exception {
        ContextBuilder.of(this)
                .put("event", e)
                .build()
                .execVoid("doAction");
    }

    @Override
    protected boolean isShow(AnActionEvent e) {
        return ActionShowHelper.of(e)
                .fileNameMatch(".*Assembler\\.java")
                .classWithAnnotation(AnnotationNames.MAPPER)
                .elementType(PsiMethod.class)
                .isShow();
    }

    @Func
    private void doAction(Set<String> ignoreFields, PsiModifierList curMethodModifierList,
                          PsiElementFactory elementFactory, WriteActions writeActions) {
        ignoreFields.forEach(field -> {
            PsiAnnotation anno = elementFactory.createAnnotationFromText(
                    "@Mapping(target = \"" + field + "\", ignore = true)", null);
            writeActions.add(() -> curMethodModifierList.add(anno));
        });
        writeActions.run();
    }

    @Var
    private PsiModifierList curMethodModifierList(PsiMethod curMethod) {
        return PsiTreeUtil.getChildOfType(curMethod, PsiModifierList.class);
    }

    /**
     * 返回类的字段列表
     */
    @Var
    private Set<String> returnFields(PsiMethod curMethod, Project project) {
        return Optional.ofNullable(curMethod.getReturnType())
                .flatMap(type -> Optional.ofNullable(
                        IndexUtils.getClassByQualifiedName(project, type.getCanonicalText())))
                .map(psiClass -> {
                    List<String> list = PsiUtils.getAllSetterName(psiClass)
                            .collect(Collectors.toList());
                    // 处理XXXRecord的valueXX方法
                    list.addAll(Arrays.stream(psiClass.getAllMethods())
                            .filter(method -> {
                                PsiModifierList modifierList = method.getModifierList();
                                return modifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
                                        !modifierList.hasModifierProperty(PsiModifier.STATIC);
                            })
                            .filter(method -> method.getName().matches("value\\d+"))
                            .map(PsiMethod::getName)
                            .collect(Collectors.toList()));
                    return list;
                })
                // 保持顺序
                .map(list -> (Set<String>) new LinkedHashSet<>(list))
                .orElse(Collections.emptySet());
    }

    /**
     * 参数字段列表
     */
    @Var
    private Set<String> parameterArgs(PsiMethod curMethod, Project project) {
        Set<String> args = new LinkedHashSet<>();
        for (PsiParameter parameter : curMethod.getParameterList().getParameters()) {
            PsiClass parameterClass = IndexUtils.getClassByQualifiedName(project,
                    parameter.getType().getCanonicalText());

            boolean isCustomClass = Optional.ofNullable(parameterClass)
                    .map(PsiClass::getQualifiedName)
                    .map(name -> name.startsWith(Constant.BASE_PACKAGE_NAME))
                    .orElse(false);

            if (isCustomClass) {
                args.addAll(PsiUtils.getAllGetterName(parameterClass)
                        .collect(Collectors.toSet()));
            } else {
                args.add(parameter.getName());
            }
        }
        return args;
    }

    /**
     * 要忽略的字段列表
     */
    @Var
    private Set<String> ignoreFields(Set<String> parameterArgs, Set<String> returnFields, PsiMethod curMethod) {
        Set<String> set = new LinkedHashSet<>(returnFields);
        set.removeAll(parameterArgs);

        Arrays.stream(curMethod.getAnnotations())
                .filter(anno -> AnnotationNames.MAPPING.equals(anno.getQualifiedName()))
                .map(anno -> PsiUtils.getAnnotationValue(anno, "target", String.class))
                .filter(Objects::nonNull)
                .forEach(set::remove);

        return set;
    }
}
