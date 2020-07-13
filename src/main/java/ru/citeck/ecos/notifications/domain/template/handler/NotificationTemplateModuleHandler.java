package ru.citeck.ecos.notifications.domain.template.handler;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.controller.type.binary.BinModule;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir;
import ru.citeck.ecos.commons.utils.ZipUtils;
import ru.citeck.ecos.notifications.domain.template.dto.NotificationTemplateDto;
import ru.citeck.ecos.notifications.domain.template.dto.TemplateDataDto;
import ru.citeck.ecos.notifications.domain.template.service.NotificationTemplateService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class NotificationTemplateModuleHandler implements EcosModuleHandler<BinModule> {

    private final NotificationTemplateService templateService;

    private static final Pattern LANG_KEY_PATTERN = Pattern.compile(".*_(\\w+).*");
    private static final String DEFAULT_LANG_KEY = "en";

    @Override
    public void deployModule(@NotNull BinModule module) {
        templateService.update(toDto(module));
    }

    private NotificationTemplateDto toDto(BinModule module) {
        NotificationTemplateDto dto = new NotificationTemplateDto();

        ObjectData meta = module.getMeta();

        dto.setId(meta.get("id").asText());
        dto.setTitle(meta.get("title", MLText.class));

        EcosMemDir memDir = ZipUtils.extractZip(module.getData());
        dto.setData(getTemplateDataFromMemDir(memDir));

        return dto;
    }

    private Map<String, TemplateDataDto> getTemplateDataFromMemDir(EcosMemDir memDir) {
        Map<String, TemplateDataDto> templateData = new HashMap<>();

        memDir.getChildren().forEach(file -> {
            String langKey = getLangKeyFromFileName(file.getName());

            TemplateDataDto dataDto = new TemplateDataDto();
            dataDto.setName(file.getName());
            dataDto.setData(file.readAsBytes());

            templateData.put(langKey, dataDto);
        });

        return templateData;
    }

    private String getLangKeyFromFileName(String fileName) {
        Matcher matcher = LANG_KEY_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return DEFAULT_LANG_KEY;
    }

    @NotNull
    @Override
    public ModuleWithMeta<BinModule> getModuleMeta(@NotNull BinModule module) {
        NotificationTemplateDto dto = toDto(module);
        return new ModuleWithMeta<>(module, new ModuleMeta(dto.getId(), Collections.emptyList()));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "notification/template";
    }

    @Override
    public void listenChanges(@NotNull Consumer<BinModule> consumer) {
    }

    @Nullable
    @Override
    public ModuleWithMeta<BinModule> prepareToDeploy(@NotNull BinModule module) {
        return getModuleMeta(module);
    }


}
