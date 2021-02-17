package org.apache.ibatis.localtest.mybatis;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.localtest.Student;
import org.apache.ibatis.localtest.StudentManager;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

import java.io.Reader;
import java.util.List;

public class MybatisTest {


    @Test
    public void test1() throws Exception{

        // 这里路径没有/，是因为里面mybatis找不到会帮你加/，正常在classpath下找，必须要加/。
        Reader reader = Resources.getResourceAsReader("mybatis-config.xml");

        SqlSessionFactory build = new SqlSessionFactoryBuilder().build(reader);

        SqlSession sqlSession = build.openSession();

        StudentManager mapper = sqlSession.getMapper(StudentManager.class);

        List<Student> students = mapper.selectAllStudents();

        students.forEach(item -> System.out.println(item));
    }

}
