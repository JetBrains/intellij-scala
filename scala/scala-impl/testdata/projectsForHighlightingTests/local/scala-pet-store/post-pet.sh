#!/bin/bash

echo "This will only work one time, then you have to restart!"

echo "Creating pet..."
curl -X POST http://localhost:8080/pets -d @pet.json --header "Content-Type: application/json"

echo "Getting created pet with id 1"
curl -X GET "http://localhost:8080/pets?id=1"

echo "Listing pets..."
curl -X GET "http://localhost:8080/pets?pageSize=10&offset=0"

echo "Deleting pet..."
curl -X DELETE "http://localhost:8080/pets?id=1"
