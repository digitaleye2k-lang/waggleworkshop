# BusFinder – nearby stops, live buses & trains, best route

Free, open-source Android app (MIT licence).

**Download:** Releases → `BusFinder (latest build)` → `BusFinder.apk`. Every push to the `busfinder` branch rebuilds it.

## Features
- **Nearby** – uses GPS to list the closest bus stops and stations with distance and walking time; search any stop.
- **Departures** – next buses / trains / trams from a stop, live where operators publish it (on time / minutes late /
  cancelled / platform), auto-refresh every minute. In London, live TfL arrivals appear at the top.
- **Journey** – from your location (or anywhere) to an address, place or stop; leave now, leave at, or arrive by.
  Shows every option quickest first – bus, train, mixed, plus walking, cycling and driving times – and recommends
  the best one (time, number of changes and walking all count), with step-by-step directions.
- **Saved** – favourite stops and recent destinations.

## Data
- Journey planning, stops and timetables: [Transitous](https://transitous.org/) (community-run MOTIS API) –
  [data sources and licences](https://transitous.org/sources/). Map data © OpenStreetMap contributors.
- London live arrivals: Powered by TfL Open Data.

BusFinder follows the Transitous usage policy: open source, non-commercial, light use (journey searches only when
you tap *Find routes*), and identifies itself with a User-Agent pointing to this repository.
