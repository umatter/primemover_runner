package primemover.service.util;

import primemover.model.AgentType;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginVersion;

public class ApiTranslator {

    private ApiTranslator(){};

    public static String translateAgentType(MultiLoginAgent agent) {
        AgentType agentType = agent.getType();

        switch (agentType) {
            case MULTILOGIN -> {
                if (agent.getVersion() == MultiLoginVersion.ML6) {
                    return "multilogin";
                } else {
                    return "mlx";
                }
            }
            case CHROME -> {return "chrome";}
            case FIREFOX -> {return "firefox";}
            }
        throw new UnsupportedOperationException("unknown agent-type");
    }
}
