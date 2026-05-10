# GAME_WINDOW.md

# Web Client Style, Features, UI and Animation Guide

## Overview

This document defines the desired style, UI/UX design, Movement logic resolution, sprite rendering and animations for the main game window in BROWSER_CLIENT.md.

The backend already exists in Java and acts as the authoritative multiplayer server.

There is a frontend desktop application built with swing as an example which contains examples of sprite rendering, UI theme, level editor design etc.

The frameworks and libraries used have been defined in BROWSER_CLIENT.md, this document is to ensure the game window matches the swing app in function and appearance as much possible.

All the required assets are provided in /public/assets.

## UI layout
The swing application has a zoomable tile map, the camera can also be moved by holding and dragging the right mouse button. Regardless of whose turn it is, all players can click on any tile to see its terrain, structure and unit information. This information, as well as game and player info appears in a panel at the bottom of the game window.

## Animation Requirements
Unit sprite packs for movement and attacking contain pngs which have a grid of a unit facing the 4 cardinal directions, the first column faces east, second faces south, third faces west and 4th faces north. The number of rows varies and correspond to different frames of its movement/attack animation. When a unit moves or attacks in a given direction the sprite should update to face the correct direction, and play the animation in sequence by cycling through the animation frames.

Movement animation should match the tile path selected by the user and should appear smooth by drawing the sprite moving and changing direction along its selected path.

All units which are still available to move on a user's term should be marked with a square crosshair. All units on the map should have a health bar in the top right corner showing as a percentage of their total. It should be green at 60% or higher, yellow at 30% or higher and red otherwise

## Factory / Warmachine

The Factory and the Warmachine have the ability to build new units. When they are selected a modal interface should appear showing the available units, their name and cost. The bottom panel can be populated with the unit data and abilities

