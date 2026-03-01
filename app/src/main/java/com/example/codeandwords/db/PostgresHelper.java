package com.example.codeandwords.db;

import android.util.Log;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgresHelper {

    // Адрес для ЭМУЛЯТОРА.
    // Если запускаете на реальном телефоне, здесь нужен IP компьютера (например, 192.168.1.X)
    private static final String HOST = "10.0.2.2";
    private static final String PORT = "5432";
    private static final String DB_NAME = "postgres"; // Имя БД со скриншота
    private static final String USER = "postgres";    // Пользователь со скриншота
    private static final String PASS = "postgres";    // Ваш пароль

    public static Connection connect() {
        Connection connection = null;
        String connectionString = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DB_NAME;

        try {
            // Загружаем драйвер
            Class.forName("org.postgresql.Driver");

            // Пытаемся подключиться
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