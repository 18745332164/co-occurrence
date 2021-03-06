
function printError(error) {
    log.log(error.name + ": in " + error.fileName + " in line " + error.lineNumber + ": " + error.message + "\n" + error.stack);
}

OpenLayers.Control.Click = OpenLayers.Class(OpenLayers.Control, {
    defaultHandlerOptions: {
        'single': true,
        'double': false,
        'pixelTolerance': 0,
        'stopSingle': false,
        'stopDouble': false
    },

    initialize: function(options) {
        this.handlerOptions = OpenLayers.Util.extend(
            {}, this.defaultHandlerOptions
        );
        OpenLayers.Control.prototype.initialize.apply(
            this, arguments
        ); 
        this.handler = new OpenLayers.Handler.Click(
            this, {
                'click': this.trigger
            }, this.handlerOptions
        );
    }, 

    trigger: function(e) {
        var lonlat = getLonLatFromPixel(e.xy);
        setMarkerToPosition(lonlat);
        notifyBoxesAboutClick(lonlat);
    }
});

function getLonLatFromPixel(xy) {
    var position = map.getLonLatFromPixel(xy);
    return markerPositionToLonLat(position);
}

function setMarkerToPosition(lonlat) {
    try {
        marker.destroy();
    } catch (e) {
        printError(e);
    }
    marker = new OpenLayers.Marker(lonLatToMarkerPosition(lonlat));
    while (markers.markers.length > 0) {
        markers.removeMarker(markers.markers[0]);
    }
    markers.addMarker(marker);
    setConfigurationInURL();
    destroyAllPopups();
}

function getMarkerPosition() {
    return markerPositionToLonLat(marker.lonlat);
}

function setPositionInURL(lonlat) {
    log.log("setPositionInURL", lonlat);
    center.lon = lonlat.lon;
    center.lat = lonlat.lat;
    setConfigurationInURL();
}

function lonLatToMarkerPosition(lonLat) {
    return new OpenLayers.LonLat(lonLat.lon, lonLat.lat).transform( fromProjection, toProjection);
}

function markerPositionToLonLat(position) {
    return new OpenLayers.LonLat(position.lon, position.lat).transform( toProjection, fromProjection);
}

function setPosition(doNotPrint) {
    try {
        map.setCenter(lonLatToMarkerPosition(center), zoom);
    } catch (error) {
        if (doNotPrint) {
            log.log("Retry setting the center of the map.")
        } else {
            printError(error);
        }
        throw error;
    }
    updatePlants();
    controlsBlockMapClick();
}

var center = {lon: 13.097033948961839, lat: 52.38587459217477};
var zoom = 16;

// projection from https://wiki.openstreetmap.org/wiki/OpenLayers_Simple_Example#Add_Markers
var fromProjection;
var toProjection;
var markers;
var plants;
var ownPlants;
var marker;
var map;
var mapLayersById = {};

function onload() {
    log.log("Loading map ...");
    try{
        var click = new OpenLayers.Control.Click();

        // projection from https://wiki.openstreetmap.org/wiki/OpenLayers_Simple_Example#Add_Markers
        fromProjection = new OpenLayers.Projection("EPSG:4326");   // Transform from WGS 1984
        toProjection   = new OpenLayers.Projection("EPSG:900913"); // to Spherical Mercator Projection
        markers = new OpenLayers.Layer.Markers( translate("Location") );
        plants = new OpenLayers.Layer.Markers( translate("Plants") );
        ownPlants = new OpenLayers.Layer.Markers( translate("My Plants") );
        marker = new OpenLayers.Marker(lonLatToMarkerPosition(center));

        markers.addMarker(marker);

        var layer_earth = new OpenLayers.Layer.OSM(
            translate("Satellite"),
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${z}/${y}/${x}/",
            {numZoomLevels: 17});
        layer_earth.attribution = "Source: Esri, DigitalGlobe, GeoEye, Earthstar Geographics, CNES/Airbus DS, USDA, USGS, AeroGRID, IGN, and the GIS User Community"; // from https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/
        layer_earth.persistentId = "Satellite";
        var layer_osm = new OpenLayers.Layer.OSM(
            translate("Street Map"),
            "http://a.tile.openstreetmap.org/${z}/${x}/${y}.png",
            {numZoomLevels: 19});
        layer_osm.attribution = '?? <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
        layer_osm.persistentId = "Mapnik";
        
        mapLayersById.osm = layer_osm;
        mapLayersById.earth = layer_earth;

        var unsortedMapLayers = [
            // see https://wiki.openstreetmap.org/wiki/OpenLayers_Simple_Example#Extensions
            //    new OpenLayers.Layer.OSM(),
            layer_osm,
            layer_earth,
            //    new OpenLayers.Layer.WMS( "OpenLayers WMS", "http://vmap0.tiles.osgeo.org/wms/vmap0?", {layers: 'basic'} ),
            //    new OpenLayers.Layer.OSM("OpenTopoMap", "https://{a|b|c}.tile.opentopomap.org/{z}/{x}/{y}.png", {numZoomLevels: 19}),
            ];
        var layers = [];

        var VISIBLE_LAYER = "visibleLayer";
        function showRememberedLayer() {
            var visibleLayerName = getCookie(VISIBLE_LAYER);
            log.log("visibleLayerName: " + visibleLayerName);
            unsortedMapLayers.forEach(function (layer, index) {
                if (layer.persistentId == visibleLayerName) {
                    layers.unshift(layer);
                } else {
                    layers.push(layer);
                }
            });
        }
        function rememberWhichLayerIsShown() {
            map.events.register("changelayer", this, function(e){
                var layer = e.layer;
                // from https://gis.stackexchange.com/q/110114
                if (layer.visibility && unsortedMapLayers.includes(layer)) {
                    log.log("Change to layer: " + layer.persistentId);
                    setCookie(VISIBLE_LAYER, layer.persistentId);
                }
            });
        }
        showRememberedLayer();
        layers.push(markers);
        layers.push(plants);
        layers.push(ownPlants);

        map = new OpenLayers.Map({
            div: "map",
            layers: layers,
            controls: [],
        /*    controls: [
                new OpenLayers.Control.Navigation({
                    dragPanOptions: {
                        enableKinetic: true
                    }
                }),
                click,
        //        new OpenLayers.Control.Attribution(),
                // from https://gis.stackexchange.com/a/83195
          //      new OpenLayers.Control.Navigation(),
          //      new OpenLayers.Control.PanPanel(),
        //        new OpenLayers.Control.ZoomPanel()
            ],*/
        });

        attribution = new OpenLayers.Control.Attribution();
        map.addControls([
            // from https://gis.stackexchange.com/a/83195
            new OpenLayers.Control.Zoom(),
            new OpenLayers.Control.LayerSwitcher(),
            new OpenLayers.Control.Navigation(),
            attribution,
            click,
            getGPSButton(),
        ]);

        click.activate();

        //try {
        //    setPosition(true);
        //} catch(error) {
            // size is sometimes null. https://github.com/openlayers/ol2/issues/669
            //map.size = {"w": document.body.clientWidth, "h": document.body.clientHeight} // could be a solution
        setTimeout(configurationOnLoad, 100);
        //}
        rememberWhichLayerIsShown();

        map.events.register("moveend", map, function(e){
            // see https://gis.stackexchange.com/a/26619
            center = markerPositionToLonLat(map.center);
            updatePlants();
            setConfigurationInURL();
        });
        
        addBoxesToMap();
        loadOwnPlants();
        
    } catch(error) {
        printError(error)
        throw error;
    }
    log.log("Done loading map.");
}

window.addEventListener("load", function() {
    onNotifyThatTheTranslationsAreLoaded(onload);
});

