import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class TestENVARS {
    @Test
    public void check(){
        String value = System.getenv("TPF_CONFIG_PATH");
        System.out.println("TPF_CONFIG_PATH = " + value);
        assertNotNull("Environment variable TPF_CONFIG_PATH should be set", value);
    }
}
