package org.apache.ibatis.localtest.jdbc;

import org.apache.ibatis.localtest.Student;

import java.util.List;

public class JDBCTest {
    public static void main(String[] args) throws Exception{
        List<Student> studentList = StudentManager.getInstance().querySomeStudents("张三");
        for (Student student : studentList) {
            System.out.println(student);
        }
    }
}
