#!/bin/bash

echo "Starting dev container setup..."

# Build the project
echo "Building project..."
./gradlew build
echo "Project built successfully"

echo "Starting project"
./gradlew run 
echo "Dev container setup complete!"