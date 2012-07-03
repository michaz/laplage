package playground.mzilske.latitude;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graphs;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.graph.UndirectedSubgraph;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.network.NetworkWriter;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import com.google.api.client.googleapis.auth.oauth2.draft10.GoogleAccessProtectedResource;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.latitude.Latitude;
import com.google.api.services.latitude.model.Location;

public class HelloLatitude {

	private static final String COORDINATE_SYSTEM = TransformationFactory.DHDN_GK4;
	private static CoordinateTransformation t = TransformationFactory.getCoordinateTransformation("WGS84", COORDINATE_SYSTEM);

	public static void main(String[] args) {

		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem(COORDINATE_SYSTEM);
		config.otfVis().setMapOverlayMode(true);
		MatsimRandom.reset(config.global().getRandomSeed());
		if (config.getQSimConfigGroup() == null) {
			config.addQSimConfigGroup(new QSimConfigGroup());
		}
		Scenario scenario = ScenarioUtils.createScenario(config);

		getLatitude(scenario);
		new ConfigWriter(scenario.getConfig()).write("output/config.xml");
		new NetworkWriter(scenario.getNetwork()).write("output/network.xml");
		new PopulationWriter(scenario.getPopulation(), scenario.getNetwork()).write("output/population.xml");


	}

	private static void getLatitude(Scenario scenario) {
		String SCOPE = "https://www.googleapis.com/auth/latitude.all.best";
		JacksonFactory jsonFactory = new JacksonFactory();
		HttpTransport transport = new NetHttpTransport();
		OAuth2ClientCredentials.errorIfNotSpecified();
		GoogleAccessProtectedResource accessProtectedResource;
		try {
			accessProtectedResource = OAuth2Native.authorize(transport,
					jsonFactory,
					new LocalServerReceiver(),
					null,
					"google-chrome",
					OAuth2ClientCredentials.CLIENT_ID,
					OAuth2ClientCredentials.CLIENT_SECRET,
					SCOPE);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Latitude latitude = new Latitude(new NetHttpTransport(), accessProtectedResource, jsonFactory);
		try {

			String minTime = Long.toString(new DateTime(2012,5,2,2,0).getMillis());
			String maxTime = Long.toString(new DateTime(2012,5,2,23,59).getMillis());
			List<Location> locations = latitude.location.list().setGranularity("best").setMinTime(minTime).setMaxTime(maxTime).setMaxResults("1000").execute().getItems();
			sortLocations(locations);
			filterLocations(locations);
			System.out.println("Before segmentation: " + locations.size());
			List<List<Location>> segmentation = segmentLocations(locations);
			System.out.println("After segmentation: " + segmentation.size());

			List<Segment> segments = classifySegments(segmentation);
			List<SignificantLocation> significantLocations = findSignificantLocations(segments);

			createLinks(scenario, segmentation);
			createActivities(scenario, segments, significantLocations);



		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



	}

	private static List<SignificantLocation> findSignificantLocations(List<Segment> segments) {
		List<SignificantLocation> result = new ArrayList<SignificantLocation>();
		
		
		List<Segment> significantActivities = new ArrayList<Segment>();
		for (Segment segment : segments) {
			if (segment.isSignificant) {
				significantActivities.add(segment);
			}
		}
		
		
		// Idiotische Art, in quadratischer Zeit den euklidischen MST zu berechnen.
		// Das natürlich beizeiten durch O(nlogn)-Algorithmus ersetzen.
		UndirectedGraph<Segment, DefaultWeightedEdge> g;
		g = new SimpleWeightedGraph<Segment, DefaultWeightedEdge>(DefaultWeightedEdge.class);
		for (Segment segment : significantActivities) {
			g.addVertex(segment);
		}
		for (int i=0; i < significantActivities.size(); ++i) {
			for (int j=i+1; j< significantActivities.size(); ++j) {
				Segment v1 = significantActivities.get(i);
				Segment v2 = significantActivities.get(j);
				Graphs.addEdge(g, v1, v2, CoordUtils.calcDistance(centroid(v1.locations), centroid(v2.locations)));
			}
		}
		KruskalMinimumSpanningTree<Segment, DefaultWeightedEdge> mst = new KruskalMinimumSpanningTree<Segment, DefaultWeightedEdge>(g);

		Set<DefaultWeightedEdge> edges = mst.getEdgeSet();
		System.out.println("Weights:");
		for (DefaultWeightedEdge edge : edges) {
			System.out.println(g.getEdgeWeight(edge));
		}

		Set<DefaultWeightedEdge> shortEdges = new HashSet<DefaultWeightedEdge>();
		for (DefaultWeightedEdge edge : edges) {
			if (g.getEdgeWeight(edge) < 50) {
				shortEdges.add(edge);
			}
		}

		UndirectedSubgraph<Segment, DefaultWeightedEdge> subgraph = new UndirectedSubgraph<Segment, DefaultWeightedEdge>(g, g.vertexSet(), shortEdges);
		ConnectivityInspector<Segment, DefaultWeightedEdge> connectivityInspector = new ConnectivityInspector<Segment, DefaultWeightedEdge>(subgraph);
		List<Set<Segment>> clusters = connectivityInspector.connectedSets();
		int locationId = 0;
		for (Set<Segment> cluster : clusters) {
			System.out.println(cluster);
			SignificantLocation significantLocation = new SignificantLocation();
			significantLocation.id = locationId++;
			result.add(significantLocation);
			for (Segment activity : cluster) {
				activity.atLocation = significantLocation;
			}
			
		}
		
		return result;
	}

	private static List<Segment> classifySegments(List<List<Location>> segmentation) {
		List<Segment> result = new ArrayList<Segment>();
		for (List<Location> locations : segmentation) {
			Segment segment = new Segment();
			segment.locations = locations;
			DateTime start = new DateTime(getTime(locations.get(0)));
			DateTime end = new DateTime(getTime(locations.get(locations.size()-1)));
			long durationInMinutes = new Duration(start, end).getStandardMinutes();
			if (durationInMinutes > 5) {
				segment.isSignificant = true;
			} else {
				segment.isSignificant = false;
			}
			result.add(segment);
		}
		return result;
	}

	private static List<List<Location>> segmentLocations(List<Location> locations) {
		List<List<Location>> result = new ArrayList<List<Location>>();
		List<Location> segment = new ArrayList<Location>();
		result.add(segment);
		for (Location location : locations) {
			if (!clusterCriterion(segment, location)) {
				segment = new ArrayList<Location>();
				result.add(segment);
			}
			Iterator<Location> locationsInSegment = segment.iterator();
			while (locationsInSegment.hasNext()) {
				Location locationInSegment = locationsInSegment.next();
				if (liesCompletelyIn(location, locationInSegment)) {
					locationsInSegment.remove();
				}
			}
			segment.add(location);
		}
		return result;
	}

	private static boolean liesCompletelyIn(Location location, Location locationInSegment) {
		if (CoordUtils.calcDistance(getCoord(location), getCoord(locationInSegment)) < (accuracy(locationInSegment) - accuracy(location))) {
			return true;
		} else {
			return false;
		}
	}

	private static long accuracy(Location locationInSegment) {
		return ((BigDecimal) locationInSegment.getAccuracy()).longValue();
	}

	private static boolean clusterCriterion(List<Location> segment, Location location) {
		if (segment.isEmpty()) return true;
		return CoordUtils.calcDistance(getCoord(segment.get(0)), getCoord(location)) - accuracy(segment.get(0)) - accuracy(location) <= 70.0;
	}

	private static void sortLocations(List<Location> locations) {
		Collections.sort(locations, new Comparator<Location>() {

			@Override
			public int compare(Location arg0, Location arg1) {
				return new Long(getTime(arg0)).compareTo(new Long(getTime(arg1)));
			}

		});
	}

	private static void filterLocations(List<Location> locations) {
		Iterator<Location> i = locations.iterator();
		while (i.hasNext()) {
			Location l = i.next();
			if (l.getAccuracy() == null) {
				i.remove();
			}
		}
	}

	private static void createLinks(Scenario scenario, List<List<Location>> segmentation) {
		Coord prev = null;
		Node prevNode = null;
		for (List<Location> location : segmentation) {
			Location representative = location.get(0);
			Coord coord = getCoord(representative);
			Node node = scenario.getNetwork().getFactory().createNode(new IdImpl((String) representative.getTimestampMs()), coord);
			scenario.getNetwork().addNode(node);
			if (prev != null) {
				Link link = scenario.getNetwork().getFactory().createLink(new IdImpl(prevNode.getId().toString() + node.getId().toString()), prevNode, node);
				scenario.getNetwork().addLink(link);
			}
			prev = coord;
			prevNode = node;
		}
	}

	private static long getTime(Location location) {
		return Long.parseLong((String) location.getTimestampMs());
	}

	private static void createActivities(Scenario scenario, List<Segment> segments, List<SignificantLocation> significantLocations) {
		Person p = scenario.getPopulation().getFactory().createPerson(new IdImpl("p"));
		Plan pl = scenario.getPopulation().getFactory().createPlan();
		int nAct = 0;
		Activity prev = null;
		List<Segment> planElement = new ArrayList<Segment>();
		for (Segment segment : segments) {
			List<Location> locations = segment.locations;
			Location representative = locations.get(0);
			long miliseconds = getTime(representative);
			DateTime start = new DateTime(getTime(locations.get(0)));
			DateTime end = new DateTime(getTime(locations.get(locations.size()-1)));
			System.out.println(locations + " " + new Date(miliseconds));			
			if(segment.isSignificant) {
				Coord coord = centroid(segment.locations);
				double startTime = start.getMillisOfDay() / 1000;
				double endTime = end.getMillisOfDay() / 1000;
				if (prev != null) {
					Leg leg = scenario.getPopulation().getFactory().createLeg("unknown");
					leg.setTravelTime(startTime - prev.getEndTime());
					pl.addLeg(leg);
					Route route = new GenericRouteImpl(null, null);
					double distance;
					if (planElement.isEmpty()) {
						distance = CoordUtils.calcDistance(prev.getCoord(), coord);
					} else {
						distance = CoordUtils.calcDistance(prev.getCoord(), centroid(planElement.get(0).locations)) 
								+ measure(planElement)
								+ CoordUtils.calcDistance(centroid(planElement.get(planElement.size()-1).locations), coord);
					}
					route.setDistance(distance);
					leg.setRoute(route);
				}
				planElement.clear();
				
				Activity activity = scenario.getPopulation().getFactory().createActivityFromCoord("unknown", coord);
				activity.setStartTime(startTime);
				activity.setEndTime(endTime);
				activity.setType("act" + (++nAct) + "_" + new Duration(start, end).getStandardMinutes() + "_" + segment.atLocation);
				pl.addActivity(activity);
				System.out.println(activity);
				prev = activity;
			} else {
				planElement.add(segment);
			}
		}
		p.addPlan(pl);
		scenario.getPopulation().addPerson(p);
	}

	private static double measure(List<Segment> planElement) {
		double result = 0.0;
		Coord prev = null;
		for (Segment segment : planElement) {
			Coord sis = centroid(segment.locations);
			if (prev != null) {
				result += CoordUtils.calcDistance(prev, sis);
			}
			prev = sis;
		}
		return result;
	}

	private static Coord getCoord(Location location) {
		return t.transform(new CoordImpl(((BigDecimal) location.getLongitude()).doubleValue(), ((BigDecimal) location.getLatitude()).doubleValue()));
	}



	private static double accuracy(List<Location> segment) {
		double result = 0;
		for (Location location : segment) {
			result += accuracy(location);
		}
		return result / segment.size();
	}

	private static Coord centroid(List<Location> segment) {
		CoordImpl result = new CoordImpl(0,0);
		for (Location location : segment) {
			Coord coord = getCoord(location);
			result.setXY(result.getX() + coord.getX(), result.getY() + coord.getY());
		}
		result.setXY(result.getX() / segment.size(), result.getY() / segment.size());
		return result;
	}

}
