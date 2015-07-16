package playground.mzilske.latitude;


import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.otfvis.OTFVis;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class PlayLatitude {

	public static void main(String[] args) {
		Config config = ConfigUtils.loadConfig("output/config.xml");
		config.network().setInputFile("output/network.xml");
		config.plans().setInputFile("output/population.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		OTFVis.playScenario(scenario);
	}

}
