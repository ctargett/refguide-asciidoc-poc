<#include "header.ftl">

	<#include "menu.ftl">

	<div class="page-header">
		<h1>Apache Solr Reference Guide</h1>
	</div>
	<#list pages as page>
  		<#if (page.status == "published")>
  			<a href="${page.uri}"><h2><#escape x as x?xml>${page.title}</#escape></h2></a>
			<#if page.description??>
				<p><em>${page.description}</em></p>
				<#else><p>No description</p>
			</#if>
  			<p>${published_date?string("dd MMMM yyyy")}</p>
  		</#if>
  	</#list>

	<hr />

<#include "footer.ftl">
