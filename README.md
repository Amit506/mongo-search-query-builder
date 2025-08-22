# Mongo Search Query Builder

This project provides a simple and flexible way to construct MongoDB queries using a fluent API. It includes two main classes: `QueryBuilder` and `SearchCriteria`.

## Features

- **QueryBuilder**: A class that allows you to build MongoDB queries by adding search criteria.
- **SearchCriteria**: A class that represents the criteria used for searching, including the field, operation, and value.

## Getting Started

To use the Mongo Search Query Builder, follow these steps:

1. **Clone the repository**:
   ```
   git clone https://github.com/yourusername/mongo-search-query-builder.git
   ```

2. **Navigate to the project directory**:
   ```
   cd mongo-search-query-builder
   ```

3. **Build the project using Maven**:
   ```
   mvn clean install
   ```

4. **Use the QueryBuilder in your application**:
   ```java
   QueryBuilder queryBuilder = new QueryBuilder();
   SearchCriteria criteria = new SearchCriteria("name", "=", "John Doe");
   queryBuilder.addCriteria(criteria);
   String query = queryBuilder.build();
   ```

## Example

Here is an example of how to use the `QueryBuilder` and `SearchCriteria` classes:

```java
SearchCriteria criteria1 = new SearchCriteria("age", ">", 25);
SearchCriteria criteria2 = new SearchCriteria("city", "=", "New York");

QueryBuilder queryBuilder = new QueryBuilder();
queryBuilder.addCriteria(criteria1);
queryBuilder.addCriteria(criteria2);

String mongoQuery = queryBuilder.build();
System.out.println(mongoQuery);
```

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue for any suggestions or improvements.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.