package org.wildfly.util.yaml.anchor.template;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.representer.Representer;
/**
 * A utility to take an input YAML file containing aliases, and outputting
 * a YAML file with the aliases 'exanded'.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class Expander {

    private final String input;

    public Expander(String input) {
        this.input = input;
    }

    public String expandInput() throws Exception {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        // Fix below - additional configuration
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(new Constructor(), new NonAnchorRepresenter(), options);
        Object o;
        try {
            o = yaml.load(new FileReader(new File(input)));
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Specified input file '" + input + "' does not exist.");
        }

        // This expands all the aliases but still keeps the defaults key
        String out = yaml.dump(o);

        // Load it up again and remove the 'defaults' entry
        o = yaml.load(out);
        LinkedHashMap<String, ?> map = (LinkedHashMap<String, ?>) o;
        Set<String> anchorKeys = new HashSet<>();
        for (String key : map.keySet()) {
            if (key.startsWith("x-")) {
                anchorKeys.add(key);
            }
        }
        for (String key : anchorKeys) {
            map.remove(key);
        }

        out = yaml.dump(map);

        out =
                "# Do not edit this file directly!!!\n\n" +
                "# Instead edit the template file, and use the generator to create it." +
                "# The generator is at https://github.com/kabir/yaml-alias-expander"
                        + "\n\n" + out;
        return out;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalStateException("Needs 2 parameters: <input-template-path> <output-path>");
        }

        if (args[0].equals(args[1])) {
            throw new IllegalStateException("The input and output files can't be the same");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(args[0])))) {
            String line = reader.readLine();
            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = reader.readLine();
            }
        }

        Expander expander = new Expander(sb.toString());
        String expanded = expander.expandInput();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(args[1])))) {
            writer.write(expanded);
        }
    }


    // Taken from https://stackoverflow.com/questions/18202548/java-snakeyaml-prevent-dumping-reference-names
    private class NonAnchorRepresenter extends Representer {

        public NonAnchorRepresenter() {
            this.multiRepresenters.put(Map.class, new RepresentMap() {
                public Node representData(Object data) {
                    return representWithoutRecordingDescendents(data, super::representData);
                }
            });
        }

        protected Node representWithoutRecordingDescendents(Object data, Function<Object,Node> worker) {
            Map<Object,Node> representedObjectsOnEntry = new LinkedHashMap<Object,Node>(representedObjects);
            try {
                return worker.apply(data);
            } finally {
                representedObjects.clear();
                representedObjects.putAll(representedObjectsOnEntry);
            }
        }

    }
}
