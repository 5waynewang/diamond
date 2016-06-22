<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<frameset cols="17%,*">
    <frame id="leftFrame" name="leftFrame" src="jsp/admin/menu.jsp" />
    <frame id="rightFrame" name="rightFrame" src="<c:url value='/admin?method=listConfig&dataId=&group=&pageNo=1&pageSize=15'/>" />
</frameset>

