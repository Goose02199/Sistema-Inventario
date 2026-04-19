FROM eclipse-temurin:17-alpine

WORKDIR /app

COPY src/ /app/src/

RUN mkdir out && javac -d out src/common/*.java src/server/*.java

ENV SERVER_ID=1
ENV PUBLIC_PORT=5001
ENV CLUSTER_PORT=6001

EXPOSE 5001 6001

CMD ["sh", "-c", "java -cp out server.InventoryServer $SERVER_ID $PUBLIC_PORT $CLUSTER_PORT"]