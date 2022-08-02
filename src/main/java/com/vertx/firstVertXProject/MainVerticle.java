package com.vertx.firstVertXProject;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;



public class MainVerticle extends AbstractVerticle {


  @Override
  public void start() {
    MySQLConnectOptions options = new MySQLConnectOptions()
      .setPort(3306)
      .setHost("localhost")
      .setDatabase("vertx_test")
      .setUser("root")
      .setPassword("");

    Pool pool = Pool.pool(vertx, options, new PoolOptions().setMaxSize(4));

    //creatingData from method below
    //createSomeData(pool);
    //create router to use for routes
    Router router = Router.router(vertx);


    //simple get route for the static route /
    router.route("/").handler(routingContext -> {
      HttpServerResponse res = routingContext.response();
      res
        .putHeader("content-type", "text/html")
        .end("</pre><h1>Hello from my first Vert.x app</h1><pre>");
    });

    //when route is hit, call getAll method below
    router.get("/api/articles").handler(getAll(pool));
    //first line enables the reading of the request body for all routes under /api/articles
    //second line routes the POST and once its hit, call the addOne method below
    router.route("/api/articles*").handler(BodyHandler.create());
    router.post("/api/articles").handler(addOne(pool));
    //the path passes through the id of the article we want to delete and calls deleteOne method
    router.delete("/api/articles/:id").handler(deleteOne(pool));
    //route passes an article by id and access it
    router.get("/api/articles/:id").handler(getOne(pool));
    //route passes an article by ID to update
    router.put("/api/articles/:id").handler(updateOne(pool));
    /*
    * Routes request on /assets/{insertAssetNameHere} to resources stored in the assets folder
    * ex: http://localhost:8888/assets/index.html will serve the index.html page
    * */
    router.route("/assets/*").handler(StaticHandler.create("assets"));

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .onSuccess(server ->
        System.out.println("HTTP server started on port " + server.actualPort()))
      .onFailure(event -> System.out.println("Failed to start HTTP server: " + event.getMessage()));

  }


  //create a readingList using the Article class
  private void createSomeData(Pool pool) {
    Article article1 = new Article(
      "Fallacies of distributed computing",
      "https://en.wikipedia.org/wiki/Fallacies_of_distributed_computing");

    pool
      .query("CREATE TABLE if not exists articles(id int NOT NULL AUTO_INCREMENT,title varchar(255), url varchar(255), PRIMARY KEY(id))")
      .execute()
      .compose(r ->
        pool
          .query("INSERT INTO articles (title, url) VALUES ('"+article1.getTitle()+"', '"+article1.getUrl()+"')")
          .execute()
      );
  }
  //this shows all the values of the readingList in JSON format *READ*
  private Handler<RoutingContext> getAll(Pool pool){
   return routingContext -> {
     JsonArray jsonArray = new JsonArray();
     pool
       .query("SELECT * FROM articles")
       .execute(ar -> {
         if(ar.succeeded()) {
           RowSet<Row> rows = ar.result();
           for (Row row : rows) {
            JsonObject json = row.toJson();
             jsonArray.add(json);
           }
           routingContext
             .response()
             .setStatusCode(200)
             .end(Json.encodePrettily(jsonArray));
         } else {
           routingContext
             .response()
             .setStatusCode(400)
             .end(ar.cause().getMessage());
         }
       });
   };
  }
  //this adds an article to the readingList *CREATE*
  private Handler<RoutingContext> addOne(Pool pool){
    return routingContext -> {
      //create JSONObject from request body
      JsonObject articleToAdd = routingContext.getBodyAsJson();
      pool
        .query("INSERT INTO articles (title, url) VALUES ('"+articleToAdd.getString("title")+"', '"+articleToAdd.getString("url")+"')")
        .execute(ar -> {
          if(ar.succeeded()){
            routingContext
              .response()
              .setStatusCode(200)
              .end();
          } else {
            routingContext
              .response()
              .setStatusCode(400)
              .end(ar.cause().getMessage());
          }
        });
    };
 }
  //this deletes an article from the readingList *DELETE*
  private Handler<RoutingContext> deleteOne(Pool pool) {
    return routingContext -> {
      //get id from parameters of request
      String id = routingContext.request().getParam("id") ;
      try{
        //convert id to integer
        Integer idAsInteger = Integer.valueOf(id);
        pool
          .query("DELETE FROM articles WHERE id="+idAsInteger+"")
          .execute(ar -> {
            if(ar.succeeded()){
              routingContext
                .response()
                .setStatusCode(200)
                .end();
            } else {
              routingContext
                .response()
                .setStatusCode(400)
                .end(ar.cause().getMessage());
            }
          });
      } catch(NumberFormatException nfe) {
        routingContext.response().setStatusCode(400).end();
        // ^ just in case String ID could not be converted
      }
    };
  }
//  //this gets an article by a specific id passed in
  private Handler<RoutingContext> getOne(Pool pool) {
    return routingContext -> {
      String id = routingContext.request().getParam("id");
      try {
        Integer idAsInteger = Integer.valueOf(id);
        pool
          .query("SELECT * FROM articles WHERE id="+idAsInteger+"")
          .execute(ar -> {
            if(ar.succeeded()){
              RowSet<Row> rows = ar.result();
              for (Row row : rows) {
                JsonObject article = row.toJson();
                routingContext
                  .response()
                  .setStatusCode(200)
                  .end(Json.encodePrettily(article));
              }
            } else {
              routingContext
                .response()
                .setStatusCode(400)
                .end(ar.cause().getMessage());
            }
          });
      } catch (NumberFormatException e) {
        routingContext.response().setStatusCode(400).end();
      }
    };
  }

  private Handler<RoutingContext> updateOne(Pool pool) {
    return routingContext -> {
      String id = routingContext.request().getParam("id");
      JsonObject body = routingContext.getBodyAsJson();
      String title = body.getString("title");
      String url = body.getString("url");
      try {
        Integer idAsInteger = Integer.valueOf(id);
        pool
          .query("UPDATE articles SET title='"+title+"', url='"+url+"' WHERE id="+idAsInteger+"")
          .execute(ar -> {
            if (ar.succeeded()){
              routingContext
                .response()
                .setStatusCode(200)
                .end();
            } else {
              routingContext
                .response()
                .setStatusCode(400)
                .end(ar.cause().getMessage());
            }
          });
      } catch (NumberFormatException e) {
        routingContext.response().setStatusCode(400).end();
      }
    };
  }

}
