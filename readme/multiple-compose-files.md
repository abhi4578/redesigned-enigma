# Multiple Compose files overriding examples
## Use different config file i.e. config-x.json than the standard two
  -  The secrets directory structure will be :
        ```sh
        secrets/
        ├── all-verticles-configs
        │   ├── config-depl.json
        │   ├── config-dev.json
        │   └── config-x.json
        └── one-verticle-configs
        ```
   - Create a non-git versioned named `docker-compose.temp.yml` file with following contents:
        ```sh 
        version: '3.7'
        networks:
        calc-net:
            driver: bridge
        services:
        calc:
            image: iudx/calc-dev:latest
            volumes:
            - ./secrets/all-verticles-configs/config-x.json:/usr/share/app/secrets/all-verticles-configs/config.json
            ports:
            - "8080:80"
            networks: 
            - calc-net
        ```
   - Command to bring up the file server container is :
        ```sh
        docker-compose -f docker-compose.yml -f docker-compose.temp.yml up -d 
        ```
## Binding the ports of clustered calc container to host
   - Create a non-git versioned named `docker-compose.temp.yml` file with following contents:
        ```sh
        version: '3.7'
        networks:
        overlay-net:
            external: true      
            driver: overlay
        services:
        calc:
            image: iudx/calc-depl:latest
            volumes:
            - ./secrets/all-verticles-configs/config-depl.json:/usr/share/app/secrets/all-verticles-configs/config.json
            ports:
            - "80:8080"
            - "9000:9000"
            networks: 
            - overlay-net

        ```
   - Command to bring up the latest-ingestion-pipeline container is :
        ```sh
        docker-compose -f docker-compose.yml -f docker-compose.temp.yml up -d 
        ```
## NOTE
- For overriding other miscellaneous configurations through multiple-compose files i.e. docker-compose.yml and docker-compose.temp.yml, refer [here](https://docs.docker.com/compose/extends/). 
