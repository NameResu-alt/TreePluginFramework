package org.treepluginframework;

import org.treepluginframework.component_architecture.TPF;
import org.treepluginframework.component_architecture.TPFValueRepository;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

public class Main {
    public static void main(String[] args)
    {
        TPF check = new TPF();

        File findFile = new File("C:\\Users\\Banka\\Downloads\\kitpvp.yml");
        if(findFile.exists()){
            System.out.println("kitpvp.yml exists");
            check.getValueRepository().addConfigurationFile(findFile);
        }

        check.start();

        TickEvent testEvent = new TickEvent();
        TickEventAdapter adapter = new TickEventAdapter(testEvent);

        EventEntryPoint entry = check.getNodeRepository().getNode(EventEntryPoint.class);
        check.getEventDispatcher().emit(entry, adapter);
        check.getEventDispatcher().emit(entry, adapter);
        check.getEventDispatcher().emit(entry, adapter);
        HashMap<String,Class<?>> testingValues = new HashMap<>();
        testingValues.put("scout",String.class);

        HashMap<String, Object> result = check.getValueRepository().getFileValues(new TPFValueRepository.FileValueRequest("kitpvp.yml", testingValues));
        System.out.println("Have Scout key: " + result.containsKey("scout"));

    }
}
