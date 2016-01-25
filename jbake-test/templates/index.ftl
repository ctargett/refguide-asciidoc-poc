<#include "header.ftl">

	<#include "menu.ftl">

	<div class="page-header">
		<h1>Apache Solr Reference Guide</h1>
	</div>
	<#list pages as page>
  		<#if (page.status == "published")>
  			<a href="${page.uri}"><h1><#escape x as x?xml>${page.title}</#escape></h1></a>
  			<p>${published_date?string("dd MMMM yyyy")}</p>
  		</#if>
  	</#list>

	<hr />

<#include "footer.ftl">
