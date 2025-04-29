<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>User Details</title>
</head>
<body> 
<table border="1" align="center" style="width:50%">
        <thead>
            <tr>
                <th>User Id</th>
                <th>First Name</th>
                <th>Last Name</th>
                <th>Email</th>
            </tr>
        </thead>
        <tbody>
                <tr>
                    <td>${user.userid}</td>
                    <td>${user.firstName}</td>
                    <td>${user.lastName}</td>                   
                    <td>${user.email}</td>                   
                </tr>
        </tbody>
    </table> 
</body>
</html>
