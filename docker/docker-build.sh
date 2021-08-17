#!/bin/bash

docker build -t iudx/calc-dev:latest -f docker/dev.dockerfile .
docker build -t iudx/calc-depl:latest -f docker/depl.dockerfile .
