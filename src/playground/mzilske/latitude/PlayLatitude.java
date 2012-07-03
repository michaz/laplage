/*package playground.mzilske.latitude;

import java.util.Random;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.otfvis.OTFVis;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.agents.DefaultAgentFactory;
import org.matsim.core.mobsim.qsim.agents.PopulationAgentSource;
import org.matsim.core.mobsim.qsim.qnetsimengine.QNetsimEngine;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vis.otfvis.OnTheFlyServer;

import playground.mzilske.osm.JXMapOTFVisClient;

public class PlayLatitude {
	
	public static void main(String[] args) {
		
		Config config = ConfigUtils.loadConfig("output/config.xml");
		config.network().setInputFile("output/network.xml");
		config.plans().setInputFile("output/population.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		play(scenario);
	}
	
	private static void play(Scenario scenario) {
		EventsManager events = EventsUtils.createEventsManager();
		QSim qSim = new QSim(scenario, events);
//		MyActivityEngine activityEngine = new MyActivityEngine();
//		qSim.addMobsimEngine(activityEngine);
//		qSim.addActivityHandler(activityEngine);
		QNetsimEngine qNetsimEngine = new QNetsimEngine( qSim, new Random());
		qSim.addMobsimEngine(qNetsimEngine);
		qSim.addAgentSource(new PopulationAgentSource(scenario.getPopulation(), new DefaultAgentFactory(qSim), qSim));
		OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim(scenario.getConfig(), scenario, events, qSim);
		JXMapOTFVisClient.run(scenario.getConfig(), server);
		qSim.run();
	}

}*/
