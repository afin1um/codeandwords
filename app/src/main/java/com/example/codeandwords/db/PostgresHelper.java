package com.example.codeandwords.db;

import android.util.Log;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Вспомогательный класс для прямого подключения к PostgreSQL через JDBC.
// Используется только для отладки на эмуляторе (10.0.2.2 = localhost хоста).
// На реальном устройстве требуется замена HOST на IP компьютера.
public class PostgresHelper {

    private static final String HOST = "10.0.2.2";
    private static final String PORT = "5432";
    private static final String DB_NAME = "postgres";
    private static final String USER = "postgres";
    private static final String PASS = "postgres";

    public static Connection connect() {
        Connection connection = null;
        String connectionString = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DB_NAME;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(connectionString, USER, PASS);
            Log.d("PostgresConnection", "Успешное подключение к PostgreSQL!");

        } catch (ClassNotFoundException e) {
            Log.e("PostgresConnection", "Драйвер PostgreSQL не найден", e);
        } catch (SQLException e) {
            Log.e("PostgresConnection", "Ошибка SQL подключения: " + e.getMessage(), e);
        }
        return connection;
    }
}