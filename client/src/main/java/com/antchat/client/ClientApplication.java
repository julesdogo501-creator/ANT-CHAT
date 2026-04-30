package com.antchat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;

public class ClientApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(ClientApplication.class.getResource("main.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 600);

        /* Feuilles de style : chargement explicite (classpath) pour éviter tout échec silencieux depuis le FXML */
        URL style = ClientApplication.class.getResource("style.css");
        URL auth = ClientApplication.class.getResource("auth.css");
        if (style != null) {
            scene.getStylesheets().add(style.toExternalForm());
        }
        if (auth != null) {
            scene.getStylesheets().add(auth.toExternalForm());
        }
        
        // Configuration de la fenêtre pour le Glassmorphism (Fond transparent)
        scene.setFill(Color.TRANSPARENT);
        stage.initStyle(StageStyle.TRANSPARENT);
        
        stage.setTitle("ANT CHAT");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
