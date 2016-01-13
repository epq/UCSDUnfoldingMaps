package module6;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import de.fhpotsdam.unfolding.UnfoldingMap;
import de.fhpotsdam.unfolding.data.Feature;
import de.fhpotsdam.unfolding.data.PointFeature;
import de.fhpotsdam.unfolding.data.ShapeFeature;
import de.fhpotsdam.unfolding.marker.Marker;
import de.fhpotsdam.unfolding.marker.MarkerManager;
import de.fhpotsdam.unfolding.marker.SimpleLinesMarker;
import de.fhpotsdam.unfolding.utils.MapUtils;
import de.fhpotsdam.unfolding.geo.Location;
import parsing.ParseFeed;
import processing.core.PApplet;

/**
 * An applet that shows airports (and routes) on a world map.
 * 
 * @author Adam Setters and the UC San Diego Intermediate Software Development
 *         MOOC team
 *
 */
public class AirportMap extends PApplet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static int MAX_FLIGHTS = 100;

	UnfoldingMap map;
	private List<Marker> airportList;
	List<Marker> routeList;
	HashMap<Location, ArrayList<Location>> routeDisplays; // for keeping track
															// of specific route
															// and flight
															// information
															// a multimap could
															// be used instead.
	HashMap<Integer, Location> airports;
	private List<Marker> routeMarkers;
	MarkerManager<Marker> manager;

	private CommonMarker lastSelected;
	private CommonMarker lastClicked;

	public void setup() {
		// setting up PApplet
		size(800, 600, OPENGL);

		// setting up map and default events
		map = new UnfoldingMap(this, 50, 50, 750, 550);
		MapUtils.createDefaultEventDispatcher(this, map);

		// get features from airport data
		List<PointFeature> features = ParseFeed.parseAirports(this, "airports.dat");

		// list for markers, hashmap for quicker access when matching with
		// routes
		airportList = new ArrayList<Marker>();
		airports = new HashMap<Integer, Location>();

		// parse route data
		List<ShapeFeature> routes = ParseFeed.parseRoutes(this, "routes.dat");

		// Trying to reduce the number of airports that are displayed to only
		// those
		// above a certain number of routes. (It's slow. TODO: initially read in
		// the features
		// and routes into a HashMap instead of ArrayList.)
		HashMap<Integer, Integer> routeCounts = new HashMap<Integer, Integer>();
		for (ShapeFeature route : routes) {
			int source = Integer.parseInt((String) route.getProperty("source"));
			if (!routeCounts.containsKey(source)) {
				routeCounts.put(source, 1);
			} else {
				int count = routeCounts.get(source) + 1;
				routeCounts.put(source, count);
			}
		}

		// create markers from features
		for (PointFeature feature : features) {
			AirportMarker m = new AirportMarker(feature);
			m.setRadius(5);
			// airportList.add(m);

			if (routeCounts.get(Integer.parseInt(feature.getId())) != null) {
				int count = routeCounts.get(Integer.parseInt(feature.getId()));

				// Colour the airports with >= MAX_FLIGHTS routes.
				if (count >= MAX_FLIGHTS) {
					m.setColoured(true);
				}
				if (count > 0)
					airportList.add(m);
			}

			// put airport in hashmap with OpenFlights unique id for key
			airports.put(Integer.parseInt(feature.getId()), feature.getLocation());
		}

		// Remove routes that have < MAX_FLIGHTS to avoid cluttering the map.
		for (Iterator<ShapeFeature> iter = routes.listIterator(); iter.hasNext();) {
			ShapeFeature route = iter.next();
			int source = Integer.parseInt((String) route.getProperty("source"));
			int count = routeCounts.get(source);
			if (count < MAX_FLIGHTS) {
				iter.remove();
			}
		}

		routeMarkers = new ArrayList<Marker>();
		routeList = new ArrayList<Marker>();
		routeDisplays = new HashMap<Location, ArrayList<Location>>();
		for (ShapeFeature route : routes) {

			// get source and destination airportIds
			int source = Integer.parseInt((String) route.getProperty("source"));
			int dest = Integer.parseInt((String) route.getProperty("destination"));

			// get locations for airports on route
			if (airports.containsKey(source) && airports.containsKey(dest)) {
				Location sourceLoc = airports.get(source);
				Location destLoc = airports.get(dest);

				route.addLocation(sourceLoc);
				route.addLocation(destLoc);

				// add mappings to store all airport information for each route
				ArrayList<Location> destinations;
				if (!routeDisplays.containsKey(sourceLoc)) {
					destinations = new ArrayList<Location>(Arrays.asList(destLoc));
					routeDisplays.put(sourceLoc, destinations);
				} else {
					destinations = routeDisplays.get(sourceLoc);
					destinations.add(destLoc);
					routeDisplays.put(sourceLoc, destinations);
				}
			}

			SimpleLinesMarker sl = new SimpleLinesMarker(route.getLocations(), route.getProperties());
			routeList.add(sl);
		}

		manager = new MarkerManager<Marker>();
		map.addMarkerManager(manager);
		manager.addMarkers(airportList);
	}

	public void mouseClicked() {
		System.out.println(lastClicked);

		if (lastClicked != null) {
			for (Marker m : routeMarkers) {
				manager.removeMarker(m);
			}
			routeMarkers.clear();
			lastClicked = null;
		} else if (lastClicked == null) {
			Marker mark = manager.getFirstHitMarker(mouseX, mouseY);
			lastClicked = (CommonMarker) mark;
			displayRoute(mark.getLocation());
			if (routeMarkers != null) {
				manager.addMarkers(routeMarkers);
			}
		}

	}

	@Override
	public void mouseMoved() {
		// clear the last selection
		if (lastSelected != null) {
			lastSelected.setSelected(false);
			lastSelected = null;
		}
		selectMarkerIfHover(airportList);
		// loop();
	}

	// If there is a marker selected
	private void selectMarkerIfHover(List<Marker> markers) {
		// Abort if there's already a marker selected
		if (lastSelected != null) {
			return;
		}

		for (Marker m : markers) {
			CommonMarker marker = (CommonMarker) m;
			if (marker.isInside(map, mouseX, mouseY)) {
				lastSelected = marker;
				marker.setSelected(true);
				return;
			}
		}
	}

	private void displayRoute(Location airportId) {
		if (routeDisplays.get(airportId) != null) {
			ArrayList<Location> destinations = routeDisplays.get(airportId);
			for (Location d : destinations) {
				ShapeFeature route = new ShapeFeature(Feature.FeatureType.LINES);
				route.addLocation(airportId);
				route.addLocation(d);
				SimpleLinesMarker sl = new SimpleLinesMarker(route.getLocations(), route.getProperties());
				routeMarkers.add(sl);
			}
		}
	}

	public void draw() {
		background(0);
		map.draw();
	}

}
