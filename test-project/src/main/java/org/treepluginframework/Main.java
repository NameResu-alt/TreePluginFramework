package org.treepluginframework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.component_architecture.TPF;
import org.treepluginframework.component_architecture.TPFValueRepository;

import java.io.File;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args)
    {
        TPF check = new TPF();
        //TPFEntry tpfEntry = check.getEntry();

        File findFile = new File("C:\\Users\\Banka\\Downloads\\kitpvp.yml");
        if(findFile.exists()){
            System.out.println("kitpvp.yml exists");
            check.getValueRepository().addConfigurationFile(findFile);
        }


        // HookExtendTest signals the latch when connected
        HookExtendTest hook = new HookExtendTest(check);
        hook.connectToServer();
        //hook.sendDummyMessage();
        check.addMetaEventListener(hook);


        check.start();

        TickEvent testEvent = new TickEvent();
        TickEventAdapter adapter = new TickEventAdapter(testEvent);

        //tpfEntry.getNode(EventEntryPoint.class);
        EventEntryPoint entry = check.getNodeRepository().getNode(EventEntryPoint.class);

        System.out.println("Dispatcher Check: " + (entry == null));
        if(entry != null){
            Scanner scan = new Scanner(System.in);
            while(true){
                String nextLine = scan.nextLine();
                if(nextLine.equals("exit")) break;
                //tpfEntry.emitEvent(entry,adapter);
                check.getEventDispatcher().emit(entry,adapter);
                check.getEventDispatcher().printDAG();
            }
        }

        //check.getEventDispatcher().emit(entry, adapter);
        //check.getEventDispatcher().emit(entry, adapter);
        //check.getEventDispatcher().emit(entry, adapter);

        /*
        TPFValueRepository.FileValueRequest request = new TPFValueRepository.FileValueRequest("kitpvp.yml");
        request.addWantedValue("scout",String.class,null);
        request.addWantedValue("scout",Integer.class,null);
        request.addWantedValue("person",TestJson.class,null);
        check.getValueRepository().getFileValues(request);
        String stringScoutVal = request.getValue("scout",String.class);
        Integer intScoutVal = request.getValue("scout", Integer.class);
        System.out.println("Scout Value: " + stringScoutVal + " " + intScoutVal);
        //HashMap<String, Object> result = check.getValueRepository().getFileValues(new TPFValueRepository.FileValueRequest("kitpvp.yml", testingValues));
        //System.out.println("Have Scout key: " + result.containsKey("scout"));
        //if(result.containsKey())
        TestJson checkIfExists = request.getValue("person",TestJson.class);
        System.out.println("Test: " + checkIfExists);
        /*
        ObjectMapper map = new ObjectMapper();
        try {
            String test = map.writerWithDefaultPrettyPrinter().writeValueAsString(new TestJson("Name",23,true));
            System.out.println(test);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
         */
    }
}
