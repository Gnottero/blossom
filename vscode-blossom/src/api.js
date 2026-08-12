'use strict';

/*
 * I dati dell'API, caricati una volta sola e indicizzati.
 *
 * `data/api.json` è generato da `tools/generate-api.js` a partire dalle sorgenti del mod: qui non
 * c'è nessun elenco scritto a mano. Il file si legge alla prima richiesta, non all'attivazione,
 * così aprire un file `.bs` non paga il costo di chi non usa il completamento.
 */

const fs = require('fs');
const path = require('path');

let data = null;
/** simbolo → nome del modulo che lo esporta (il primo, come fa StdLib.homeModuleOf) */
let homeModule = null;
/** nome evento → descrizione */
let eventByName = null;

function load() {
    if (data) return data;
    data = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'api.json'), 'utf8'));
    homeModule = new Map();
    for (const [moduleName, module] of Object.entries(data.modules)) {
        for (const symbol of Object.keys(module.symbols)) {
            if (!homeModule.has(symbol)) homeModule.set(symbol, moduleName);
        }
    }
    eventByName = new Map(data.events.map((event) => [event.name, event]));
    return data;
}

/** Il modulo da importare per usare `symbol`, o null se non è un simbolo di libreria. */
function homeModuleOf(symbol) {
    load();
    return homeModule.get(symbol) || null;
}

function moduleNames() {
    return Object.keys(load().modules);
}

function moduleOf(name) {
    return load().modules[name] || null;
}

function symbolsOf(moduleName) {
    const module = moduleOf(moduleName);
    return module ? Object.keys(module.symbols) : [];
}

function symbolInfo(moduleName, symbol) {
    const module = moduleOf(moduleName);
    return module ? module.symbols[symbol] || null : null;
}

function events() {
    return load().events;
}

function eventInfo(name) {
    load();
    return eventByName.get(name) || null;
}

function prelude() {
    return load().prelude;
}

function keywords() {
    return load().keywords;
}

function constants() {
    return load().constants;
}

function types() {
    return load().types;
}

/**
 * Distanza di edit limitata: serve solo a dire "forse intendevi", quindi appena supera la soglia
 * si smette di calcolare invece di riempire una matrice intera.
 */
function editDistance(a, b, limit) {
    if (Math.abs(a.length - b.length) > limit) return limit + 1;
    let beforePrevious = null;
    let previous = Array.from({ length: b.length + 1 }, (_, i) => i);
    for (let i = 1; i <= a.length; i++) {
        const current = [i];
        let best = i;
        for (let j = 1; j <= b.length; j++) {
            const cost = a[i - 1] === b[j - 1] ? 0 : 1;
            current[j] = Math.min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost);
            // trasposizione: 'sqtr' per 'sqrt' è un errore di dita, non due sostituzioni
            if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
                current[j] = Math.min(current[j], beforePrevious[j - 2] + 1);
            }
            if (current[j] < best) best = current[j];
        }
        if (best > limit) return limit + 1;
        beforePrevious = previous;
        previous = current;
    }
    return previous[b.length];
}

/** Il candidato più vicino a `name` fra `candidates`, o null se nessuno è abbastanza vicino. */
function suggest(name, candidates) {
    const limit = name.length <= 4 ? 1 : 2;
    let best = null;
    let bestDistance = limit + 1;
    for (const candidate of candidates) {
        const distance = editDistance(name, candidate, limit);
        if (distance < bestDistance) {
            bestDistance = distance;
            best = candidate;
        }
    }
    return bestDistance <= limit ? best : null;
}

module.exports = {
    load,
    homeModuleOf,
    moduleNames,
    moduleOf,
    symbolsOf,
    symbolInfo,
    events,
    eventInfo,
    prelude,
    keywords,
    constants,
    types,
    suggest
};
