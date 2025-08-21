package org.treepluginframework;

import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class DAGVisualizer {
    private static boolean loop = true;

    public static void main(String[] args) {
        System.setProperty("org.graphstream.ui", "swing");

        Graph graph = new SingleGraph("TestGraph");
        graph.setAttribute("ui.stylesheet", "node { size: 30px; fill-color: lightblue; }");

        graph.addNode("A").setAttribute("ui.label", "Node A");
        graph.addNode("B").setAttribute("ui.label", "Node B");
        graph.addEdge("AB", "A", "B");

        Viewer viewer = graph.display();
        viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.EXIT);

        ViewerPipe pipe = viewer.newViewerPipe();
        pipe.addSink(graph); // Important to allow event propagation

        pipe.addViewerListener(new ViewerListener() {
            @Override public void viewClosed(String viewName) {
                loop = false;
            }

            @Override public void buttonPushed(String nodeId) {
                System.out.println("Node clicked: " + nodeId);
            }

            @Override public void buttonReleased(String nodeId) {}

            @Override public void mouseOver(String nodeId) {
                System.out.println("Mouse entered: " + nodeId);
            }

            @Override public void mouseLeft(String nodeId) {
                System.out.println("Mouse left: " + nodeId);
            }
        });

        // Event loop to receive UI interactions
        while (loop) {
            pipe.pump(); // This is essential to process events
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }
    }
}
