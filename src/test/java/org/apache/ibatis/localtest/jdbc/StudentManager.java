package org.apache.ibatis.localtest.jdbc;

import org.apache.ibatis.localtest.Student;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class StudentManager {
    private static StudentManager instance = new StudentManager();

    private StudentManager() {

    }

    public static StudentManager getInstance() {
        return instance;
    }

    public List<Student> querySomeStudents(String studentName) throws Exception {
        List<Student> studentList = new ArrayList<Student>();
        Connection connection = DBConnection.mysqlConnection;
        PreparedStatement ps = connection.prepareStatement("select * from student where Sname = ?");
        ps.setString(1, studentName);
        ResultSet rs = ps.executeQuery();

        Student student = null;
        while (rs.next()) {
            student = new Student(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4));
            studentList.add(student);
        }

        ps.close();
        rs.close();
        return studentList;
    }
}