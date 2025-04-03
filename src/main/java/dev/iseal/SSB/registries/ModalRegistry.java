package dev.iseal.SSB.registries;

import dev.iseal.SSB.utils.Utils;
import dev.iseal.SSB.utils.abstracts.AbstractCommand;
import dev.iseal.SSB.utils.abstracts.AbstractModal;
import net.dv8tion.jda.internal.utils.JDALogger;
import org.slf4j.Logger;

public class ModalRegistry {

    Logger log = JDALogger.getLog("SBB-ModalRegistry");

    private static ModalRegistry instance;
    public static ModalRegistry getInstance() {
        if (instance == null) {
            instance = new ModalRegistry();
        }
        return instance;
    }

    public void init() {
        Utils.findAllClassesInPackage("dev.iseal.SSB.modals", AbstractModal.class)
                .stream().map(clazz -> (Class<? extends AbstractModal>) clazz)
                .forEach(modalClass -> {
                    try {
                        //instantiate the class
                        AbstractModal modal = modalClass.newInstance();

                        // then get the modal with the static method, creating it.
                        AbstractModal.getModal(modalClass);
                    } catch (Exception e) {
                        log.error("Failed to register command {}: {}", modalClass.getName(), e.getMessage());
                    }
                });
    }

}
