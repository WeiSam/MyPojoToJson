import com.google.gson.JsonObject;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.codehaus.jettison.json.JSONException;
import org.fest.util.Strings;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @description: 为了yapi 创建的
 * @author: chengsheng@qbb6.com
 * @date: 2018/10/27
 */ 
public class BuildJsonForYapi extends AnAction {
    private static NotificationGroup notificationGroup;


    static {
        notificationGroup = new NotificationGroup("Java2Json.NotificationGroup", NotificationDisplayType.BALLOON, true);
    }


    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = (Editor) e.getDataContext().getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = (PsiFile) e.getDataContext().getData(CommonDataKeys.PSI_FILE);
        Project project = editor.getProject();
        PsiElement referenceAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiClass selectedClass = (PsiClass) PsiTreeUtil.getContextOfType(referenceAt, new Class[]{PsiClass.class});
        try {
            KV result=new KV();
            KV kv = getFields(selectedClass,project);
            result.set("type","object");
            result.set("title",selectedClass.getName());
            result.set("description",selectedClass.getName());
            result.set("properties",kv);
            String json = result.toPrettyJson();
            StringSelection selection = new StringSelection(json);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            String message = "Convert " + selectedClass.getName() + " to JSON success, copied to clipboard.";
            Notification success = notificationGroup.createNotification(message, NotificationType.INFORMATION);
            Notifications.Bus.notify(success, project);
        } catch (Exception ex) {
            Notification error = notificationGroup.createNotification("Convert to JSON failed.", NotificationType.ERROR);
            Notifications.Bus.notify(error, project);
        }
    }


    public static KV getFields(PsiClass psiClass, Project project) throws JSONException {
        KV kv = KV.create();

        if (psiClass != null) {
            String pName=psiClass.getName();
            for (PsiField field : psiClass.getAllFields()) {
                PsiType type = field.getType();
                String name = field.getName();
                String remark ="";
                if(field.getDocComment()!=null) {
                    remark=field.getDocComment().getText().replace("*", "").replace("/", "").replace(" ", "").replace("\n", ",").replace("\t","");
                    remark=trimFirstAndLastChar(remark,',');
                }
                // 如果是基本类型
                if (type instanceof PsiPrimitiveType) {
                    JsonObject jsonObject=new JsonObject();
                    jsonObject.addProperty("type",type.getPresentableText());
                    if(!Strings.isNullOrEmpty(remark)) {
                        jsonObject.addProperty("description", remark);
                    }
                    kv.set(name, jsonObject);
                } else if(!(type instanceof PsiArrayType) && ((PsiClassReferenceType) type).resolve().isEnum()) {
                    JsonObject jsonObject=new JsonObject();
                    jsonObject.addProperty("type","enum");
                    if(!Strings.isNullOrEmpty(remark)) {
                        jsonObject.addProperty("description", remark);
                    }
                    kv.set(name, jsonObject);
                }else {
                    //reference Type
                    String fieldTypeName = type.getPresentableText();
                    //normal Type
                    if (NormalTypes.isNormalType(fieldTypeName)) {
                        JsonObject jsonObject=new JsonObject();
                        jsonObject.addProperty("type",fieldTypeName);
                        if(!Strings.isNullOrEmpty(remark)) {
                            jsonObject.addProperty("description", remark);
                        }
                        kv.set(name, jsonObject);
                    } else if (type instanceof PsiArrayType) {
                        //array type
                        PsiType deepType = type.getDeepComponentType();
                        KV kvlist = new KV();
                        String deepTypeName = deepType.getPresentableText();
                        if (deepType instanceof PsiPrimitiveType) {
                            kvlist.set("type",type.getPresentableText());
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set("description", remark);
                            }
                        } else if (NormalTypes.isNormalType(deepTypeName)) {
                            kvlist.set("type",deepTypeName);
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set("description", remark);
                            }
                        } else {
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set(KV.by("description",remark));
                            }
                            if(!pName.equals(PsiUtil.resolveClassInType(deepType).getName())){
                                kvlist.set("properties",getFields(PsiUtil.resolveClassInType(deepType),project));
                                kvlist.set(KV.by("type","object"));
                            }else{
                                kvlist.set(KV.by("type",pName));
                            }
                        }
                        KV kv1=new KV();
                        kv1.set(KV.by("type","array"));
                        if(!Strings.isNullOrEmpty(remark)) {
                            kv1.set(KV.by("description",remark));
                        }
                        kv1.set("items",kvlist);
                        kv.set(name, kv1);
                    } else if (fieldTypeName.startsWith("List")||fieldTypeName.startsWith("Set") || fieldTypeName.startsWith("HashSet")) {
                        //list type
                        PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                        PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                        KV kvlist = new KV();
                        String classTypeName = iterableClass.getName();
                        if (NormalTypes.isNormalType(classTypeName)) {
                            kvlist.set("type",classTypeName);
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set("description", remark);
                            }
                        } else {
                            if(!Strings.isNullOrEmpty(remark)) {
                                kvlist.set(KV.by("description",remark));
                            }
                            if(!pName.equals(iterableClass.getName())){
                                kvlist.set("properties",getFields(iterableClass,project));
                                kvlist.set(KV.by("type","object"));
                            }else{
                                kvlist.set(KV.by("type",pName));
                            }
                        }
                        KV kv1=new KV();
                        kv1.set(KV.by("type","array"));
                        if(!Strings.isNullOrEmpty(remark)) {
                            kv1.set(KV.by("description",remark));
                        }
                        kv1.set("items",kvlist);
                        kv.set(name, kv1);
                    } else if(fieldTypeName.startsWith("HashMap") || fieldTypeName.startsWith("Map")){
                        //HashMap or Map
                        CompletableFuture.runAsync(()->{
                            try {
                                TimeUnit.MILLISECONDS.sleep(700);
                                Notification warning = notificationGroup.createNotification("Map Type Can not Change,So pass", NotificationType.WARNING);
                                Notifications.Bus.notify(warning, project);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        });
                    }else {
                        //class type
                        KV kv1=new KV();
                        kv1.set(KV.by("type","object"));
                        if(!Strings.isNullOrEmpty(remark)) {
                            kv1.set(KV.by("description",remark));
                        }
                        if(!pName.equals(((PsiClassReferenceType) type).getClassName())){
                            kv1.set(KV.by("properties",getFields(PsiUtil.resolveClassInType(type),project)));
                        }
                        kv.set(name,kv1);
                    }
                }
            }
        }

        return kv;
    }


    /**
     * 去除字符串首尾出现的某个字符.
     * @param source 源字符串.
     * @param element 需要去除的字符.
     * @return String.
     */
    public static String trimFirstAndLastChar(String source,char element) {
        boolean beginIndexFlag = true;
        boolean endIndexFlag = true;
        do {
            if(Strings.isNullOrEmpty(source)){break;}
            int beginIndex = source.indexOf(element) == 0 ? 1 : 0;
            int endIndex = source.lastIndexOf(element) + 1 == source.length() ? source.lastIndexOf(element) : source.length();
            source = source.substring(beginIndex, endIndex);
            beginIndexFlag = (source.indexOf(element) == 0);
            endIndexFlag = (source.lastIndexOf(element) + 1 == source.length());
        } while (beginIndexFlag || endIndexFlag);
        return source;
    }
}
