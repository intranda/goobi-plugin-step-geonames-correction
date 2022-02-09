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
		clearHighlight();
		markerLayerGroup.clearLayers();
	}
	var points = getFeaturesFromTable();
	zoomToPoints(points);
}

function zoomToPoints(points) {
	if(!points || points.length == 0) {
		return;
	}
	var view = getViewAroundFeatures(points.map(p => p.latLng));
	goobiGeonamesMap.setView(view.center, view.zoom);
	points.forEach(p => L.marker(p.latLng, p).addTo(markerLayerGroup).on("click", highlightRow));
}

function clearHighlight() {
	for(let row of document.querySelectorAll('tbody tr')) {
		row.classList.remove("highlight");
	}
}

function highlightRow(event) {
	var sourceRow = event.sourceTarget.options.sourceRow;
	if(sourceRow) {
		clearHighlight();
		sourceRow.classList.add("highlight");
		if(!elementInViewport(sourceRow)) {
			sourceRow.scrollIntoView();
		}
	}
}

function elementInViewport(element) {
	var bounding = element.getBoundingClientRect();
	return bounding.top >= 0 
		&& bounding.left >= 0 
		&& bounding.right <= window.innerWidth 
		&& bounding.bottom <= window.innerHeight;
}

function zoomToRow(event) {
	var row = event.target;
	console.log(event.target.id)
	if(event.target.id.indexOf("deleteButton") >= 0) {
		return;
	}
	while(row && row.localName != "tr") {
		row = row.parentElement;
	}
	if(!row) {
		return
	}
	clearHighlight();
	row.classList.add("highlight")
	var title = row.querySelector('td.geonames-vocab').innerText;
	var latStr = row.querySelector('td.lat').innerText;
	var lngStr = row.querySelector('td.lng').innerText
	if(latStr && lngStr) {
		markerLayerGroup.clearLayers();
		var points = [{latLng: L.latLng(parseFloat(latStr), parseFloat(lngStr)), title: title, sourceRow: row}];
		zoomToPoints(points);
	}
	event.zoomedToRow = true;
}

function getFeaturesFromTable() {
	var points = [];
	var rows = document.querySelectorAll("#geonamesTable tbody tr");
	if(rows.length == 0) {
		rows = document.querySelectorAll("#searchResultsTable tbody tr");
	}
	for(let row of rows) {
		var title = row.querySelector('td.geonames-vocab').innerText;
		var latStr = row.querySelector('td.lat').innerText;
		var lngStr = row.querySelector('td.lng').innerText;
		if(latStr && lngStr) {
			var point = {latLng: L.latLng(parseFloat(latStr), parseFloat(lngStr)), title: title, sourceRow: row};
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