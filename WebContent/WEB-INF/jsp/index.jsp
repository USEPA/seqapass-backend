<%@taglib uri="http://www.springframework.org/tags/form" prefix="form"%>
<html>
<head>
<title>Spring Landing Page</title>
</head>
<body>

	<center>
		<h1>Web Services - set up for SeqAPASS functionality</h1>
	</center>
	<br>
	<p>
		The following links are designed to be called by the SeqAPASS front-
		end web interface. Each has been constructed to perform a specific
		function so that components of the SeqAPASS tool can be developed and
		tested independently. They are available inside the EPA firewall for
		testing and demonstration purposes. More will be added periodically.
		These tools use <a
			href="http://en.wikipedia.org/wiki/Representational_state_transfer">RESTful</a>
		Web Services which connect to a MySQL database as well as file system
		files and BLAST executables.
	</p>

	<h2>Basic Lookup Functionality</h2>
	<br>
	<table border="1" align="center" style="width: 75%">
		<thead>
			<tr>
				<th>URI</th>
				<th>Button</th>

			</tr>
		</thead>
		<tbody>
			<tr>
				<td>URI Format: SeqAPASS-BE/listUsers</td>
				<td><form:form method="GET" action="/SeqAPASS-BE/listUsers">
						<input type="submit" value="Get List of Users" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/dispUser/{userId}</td>
				<td><form:form method="GET" action="/SeqAPASS-BE/dispUser/1">
						<input type="submit" value="Get A Specific User by UserID" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/listProteins</td>
				<td><form:form method="GET" action="/SeqAPASS-BE/listProteins">
						<input type="submit" value="Get List of Proteins" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/dispProtein/{accession}</td>
				<td><form:form method="GET"
						action="/SeqAPASS-BE/dispProtein/WP_003131952.1">
						<input type="submit" value="Get A Specific Protein by Accession" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/getDatabaseInfo</td>
				<td><form:form method="GET"
						action="/SeqAPASS-BE/getDatabaseInfo">
						<input type="submit" value="Get Info on Database Configuration" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/getBackEndInfo</td>
				<td><form:form method="GET"
						action="/SeqAPASS-BE/getBackEndInfo">
						<input type="submit" value="Get Info on BackEnd Configuration" />
					</form:form></td>
			</tr>
		</tbody>
	</table>


	<h2>Level 1 Functionality</h2>
	<br>
	<table border="1" align="center" style="width: 75%">
		<thead>
			<tr>
				<th>URI</th>
				<th>Button</th>

			</tr>
		</thead>
		<tbody>
			<tr>
				<td>URI Format: SeqAPASS-BE/getFasta/{accession}</td>
				<td><form:form method="GET"
						action="/SeqAPASS-BE/getFasta/2IOK_A">
						<input type="submit" value="Get Fasta Sequence" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/getFastaPath/{accession}</td>
				<td><form:form method="GET"
						action="/SeqAPASS-BE/getFastaPath/2IOK_A">
						<input type="submit" value="Get Fasta Path" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/getMaxBitScore/{accession}</td>
				<td><form:form method="GET"
						action="/SeqAPASS-BE/getMaxBitScore/2IOK_A">
						<input type="submit"
							value="Get Maximum BitScore from Self-alighnment" />
					</form:form></td>
			</tr>
			<tr>
				<td>URI Format: SeqAPASS-BE/getBLASTpResults/{accession}</td>
				<td><form:form method="GET"
						action="/SeqAPASS-BE/getBLASTpResults/2IOK_A">
						<input type="submit"
							value="Get BLASTp Results - (takes a minute or two)" />
					</form:form></td>
			</tr>
		</tbody>
	</table>

</body>
</html>