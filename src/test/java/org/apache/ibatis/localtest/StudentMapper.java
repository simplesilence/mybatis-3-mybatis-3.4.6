package org.apache.ibatis.localtest;

import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * mybatis 支持xml和annotation混合的开发方式，该接口既可以配置xml，可以在方法上使用注解方式。
 */
public interface StudentMapper {

    List<Student> selectAllStudents();

    @Select("select * from student where sId = #{id}")
    Student selectStudentById(Integer id);
}
