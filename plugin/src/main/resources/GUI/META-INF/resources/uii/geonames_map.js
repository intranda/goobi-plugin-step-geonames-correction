_debug = false;

function drawGeonamesMap() {
	window.goobiGeonamesMap = L.map('geonamesMap');
	window.markerLayerGroup = L.layerGroup();
	goobiGeonamesMap.addLayer(markerLayerGroup);
	L.tileLayer( 'http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
	    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
	    subdomains: ['a','b','c']
	}).addTo( goobiGeonamesMap );
	zoomToAll();
}

function zoomToAll(e) {
	if(e) {
		if(e.zoomedToRow) {
			return;
		}
		var mapBounds = document.querySelector("#geonamesMap").getBoundingClientRect();
		if(e.clientX > mapBounds.left && e.clientX < mapBounds.right
			&& e.clientY > mapBounds.top && e.clientY < mapBounds.bottom) {
			return;
		}
		markerLayerGroup.clearLayers();
	}
	var points = getFeaturesFromTable();
	zoomToPoints(points);
}

function zoomToPoints(points) {
	var view = getViewAroundFeatures(points.map(p => p.latLng));
	goobiGeonamesMap.setView(view.center, view.zoom);
	points.forEach(p => L.marker(p.latLng, {title: p.title}).addTo(markerLayerGroup));
}

function zoomToRow(event) {
	console.log(event)
	var row = event.target;
	while(row && row.localName != "tr") {
		row = row.parentElement;
	}
	if(!row) {
		return
	}
	var latStr = row.querySelector('td.lat').innerText;
	var lngStr = row.querySelector('td.lng').innerText
	if(latStr && lngStr) {
		markerLayerGroup.clearLayers();
		var points = [{latLng: L.latLng(parseFloat(latStr), parseFloat(lngStr))}];
		zoomToPoints(points);
	}
	event.zoomedToRow = true;
}

function getFeaturesFromTable() {
	var points = [];
	var rows = document.querySelectorAll("#geonamesTable tbody tr");
	//TODO: handle empty rows
	for(let row of rows) {
		var title = row.querySelector('td.geonames-vocab').innerText;
		var latStr = row.querySelector('td.lat').innerText;
		var lngStr = row.querySelector('td.lng').innerText;
		if(latStr && lngStr) {
			var point = {latLng: L.latLng(parseFloat(latStr), parseFloat(lngStr)), title: title};
			points.push(point);
		}
	}
	return points;
}

getViewAroundFeatures = function(features, defaultZoom, zoomPadding) {
    if(!defaultZoom) {
        defaultZoom = 5;
    }
    if(!zoomPadding) {
    	zoomPadding = 0.2;
    }
    if(!features || features.length == 0) {
        return undefined;
    } else {
        if(_debug) {
    	console.log("view around ", features);
        }
    	let bounds = L.latLngBounds();
    	features.forEach(b => bounds.extend(b));
    	console.log(bounds)
        let center = bounds.getCenter();
        let diameter = this.getDiameter(bounds);
        return {
            "zoom": diameter > 0 ?  Math.max(1, goobiGeonamesMap.getBoundsZoom(bounds.pad(zoomPadding))) : defaultZoom,
            "center": [center.lat, center.lng]
        }
    }
}

getDiameter = function(bounds) {
	if(!bounds || !bounds.isValid()) {
		return 0;
	} else {
	    let diameter = goobiGeonamesMap.distance(bounds.getSouthWest(), bounds.getNorthEast());
	    return diameter;
	}
}