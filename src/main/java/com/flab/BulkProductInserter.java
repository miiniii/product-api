package com.flab;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Random;

public class BulkProductInserter {

    private static final String[] PRODUCT_NAMES = {"딸기", "복숭아", "수박", "참외", "사과", "배", "포도", "귤", "체리", "참외",
                                                   "파인애플", "망고", "키위", "한라봉", "천혜향", "메론", "감", "레몬", "파파야", "토마토"};
    private static final int RECORD_COUNT = 16000;

    public static void main(String[] args) {
        String jdbcUrl = "jdbc:mysql://localhost:3306/PRODUCT"; // or your remote MySQL URL
        String username = "root";
        String password = "";

        String sql = "INSERT INTO products (name, price, quantity) VALUES (?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            Random rand = new Random();

            for (int i = 1; i <= RECORD_COUNT; i++) {
                String name = PRODUCT_NAMES[rand.nextInt(PRODUCT_NAMES.length)];
                int price = rand.nextInt(5000) + 1000; // 1000 ~ 5999
                int quantity = rand.nextInt(90) + 10;  // 10 ~ 99

                pstmt.setString(1, name);
                pstmt.setInt(2, price);
                pstmt.setInt(3, quantity);
                pstmt.addBatch();

                if (i % 1000 == 0) {
                    pstmt.executeBatch();
                    conn.commit();
                    System.out.println(i + "개 삽입 완료");
                }
            }

            pstmt.executeBatch();
            conn.commit();
            System.out.println("총 16000개 데이터 삽입 완료!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

